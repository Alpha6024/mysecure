package com.mysecure.mysecure.scanner.ratelimit;

import com.mysecure.mysecure.model.ApiEndpoint;
import com.mysecure.mysecure.model.TargetConfiguration;
import com.mysecure.mysecure.repository.ApiEndpointRepository;
import com.mysecure.mysecure.scanner.Finding;
import com.mysecure.mysecure.scanner.FindingSeverity;
import com.mysecure.mysecure.scanner.ScanRequest;
import com.mysecure.mysecure.scanner.ScanResult;
import com.mysecure.mysecure.scanner.ScanStatus;
import com.mysecure.mysecure.scanner.ScannerType;
import com.mysecure.mysecure.scanner.SecurityScanner;
import com.mysecure.mysecure.scanner.SkippedEndpoint;
import com.mysecure.mysecure.service.TargetConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitScanner implements SecurityScanner {

    private final TargetConfigurationService targetService;
    private final ApiEndpointRepository endpointRepository;

    @Value("${mysecure.scanners.rate-limit.request-count:20}")
    private int requestCount;

    @Value("${mysecure.scanners.rate-limit.max-request-count:30}")
    private int maxRequestCount;

    @Value("${mysecure.scanners.rate-limit.delay-ms:100}")
    private long delayMs;

    @Value("${mysecure.scanners.rate-limit.timeout-ms:5000}")
    private int timeoutMs;

    @Value("${mysecure.scanners.rate-limit.latency-increase-threshold:2.0}")
    private double latencyIncreaseThreshold;

    @Override
    public ScannerType getScannerType() {
        return ScannerType.RATE_LIMIT;
    }

    @Override
    public ScanResult scan(ScanRequest request) {
        log.info("Starting {} scan for target {}", getScannerType(), request.targetId());
        Instant startedAt = Instant.now();
        List<Finding> findings = new ArrayList<>();
        List<SkippedEndpoint> skippedEndpointsList = new ArrayList<>();
        int endpointsScanned = 0;
        int endpointsSkipped = 0;
        int totalRequestsSent = 0;

        try {
            validateConfig();

            TargetConfiguration target = targetService.getTargetById(request.targetId());
            List<ApiEndpoint> endpoints = endpointRepository.findByTargetId(request.targetId());
            
            if (endpoints.isEmpty()) {
                log.warn("No endpoints found for target {}", request.targetId());
                return createResult(request.targetId(), ScanStatus.COMPLETED, startedAt, 0, 0, 0, findings, skippedEndpointsList, "No endpoints imported for target");
            }

            RestTemplate restTemplate = createScannerRestTemplate();
            
            for (ApiEndpoint endpoint : endpoints) {
                if (shouldSkipEndpoint(endpoint)) {
                    log.debug("Skipping endpoint: {} {}", endpoint.getHttpMethod(), endpoint.getPath());
                    endpointsSkipped++;
                    skippedEndpointsList.add(createSkippedEndpoint(endpoint));
                    continue;
                }

                endpointsScanned++;
                RateLimitTestMetrics metrics = testEndpoint(target, endpoint, restTemplate);
                totalRequestsSent += metrics.requestsSent;
                
                Finding finding = analyzeMetrics(endpoint, metrics);
                if (finding != null) {
                    findings.add(finding);
                }
            }

            log.info("Completed {} scan for target {}", getScannerType(), request.targetId());
            return createResult(request.targetId(), ScanStatus.COMPLETED, startedAt, endpointsScanned, endpointsSkipped, totalRequestsSent, findings, skippedEndpointsList, null);

        } catch (Exception e) {
            log.error("Scan failed for target {}", request.targetId(), e);
            return createResult(request.targetId(), ScanStatus.FAILED, startedAt, endpointsScanned, endpointsSkipped, totalRequestsSent, findings, skippedEndpointsList, e.getMessage());
        }
    }

    private void validateConfig() {
        if (requestCount > maxRequestCount) {
            requestCount = maxRequestCount;
        }
        if (requestCount <= 0) requestCount = 10;
        if (delayMs < 0) delayMs = 0;
        if (timeoutMs <= 0) timeoutMs = 5000;
        if (latencyIncreaseThreshold <= 1.0) latencyIncreaseThreshold = 2.0;
    }

    private RestTemplate createScannerRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return new RestTemplate(factory);
    }

    private boolean shouldSkipEndpoint(ApiEndpoint endpoint) {
        if (!"GET".equalsIgnoreCase(endpoint.getHttpMethod())) {
            return true;
        }
        if (endpoint.getPath() != null && endpoint.getPath().contains("{")) {
            return true;
        }
        return false;
    }

    private com.mysecure.mysecure.scanner.SkippedEndpoint createSkippedEndpoint(ApiEndpoint endpoint) {
        String reason = "Request body unavailable";
        if (endpoint.getPath() != null && endpoint.getPath().contains("{")) {
            reason = "Path parameter unavailable";
        }
        return com.mysecure.mysecure.scanner.SkippedEndpoint.builder()
                .endpointId(endpoint.getId())
                .path(endpoint.getPath())
                .httpMethod(endpoint.getHttpMethod())
                .reason(reason)
                .build();
    }

    private static class RateLimitTestMetrics {
        int requestsSent = 0;
        int successfulResponses = 0;
        int status429Count = 0;
        int errorCount = 0;
        int authErrorCount = 0;
        int firstThrottledRequest = -1;
        long totalResponseTime = 0;
        long baselineTotalTime = 0;
        long laterTotalTime = 0;
        String retryAfter = null;
        boolean rateLimitHeadersObserved = false;
    }

    private RateLimitTestMetrics testEndpoint(TargetConfiguration target, ApiEndpoint endpoint, RestTemplate restTemplate) {
        String baseUrl = target.getTargetUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String path = endpoint.getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String url = baseUrl + path;

        log.info("Testing {} {}", endpoint.getHttpMethod(), url);
        
        RateLimitTestMetrics metrics = new RateLimitTestMetrics();

        int baselineCount = Math.min(5, requestCount / 2);
        if (baselineCount == 0) baselineCount = 1;

        for (int i = 1; i <= requestCount; i++) {
            metrics.requestsSent++;
            Instant reqStart = Instant.now();
            boolean stopEarly = false;
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
                long duration = Duration.between(reqStart, Instant.now()).toMillis();
                metrics.totalResponseTime += duration;
                
                if (i <= baselineCount) metrics.baselineTotalTime += duration;
                else metrics.laterTotalTime += duration;

                checkHeaders(response.getHeaders(), metrics);

                if (response.getStatusCode().is2xxSuccessful()) {
                    metrics.successfulResponses++;
                } else if (response.getStatusCode().value() == 429) {
                    recordThrottling(i, response.getHeaders(), metrics);
                    stopEarly = true;
                } else {
                    metrics.errorCount++;
                }
            } catch (HttpClientErrorException.TooManyRequests e) {
                long duration = Duration.between(reqStart, Instant.now()).toMillis();
                metrics.totalResponseTime += duration;
                checkHeaders(e.getResponseHeaders(), metrics);
                recordThrottling(i, e.getResponseHeaders(), metrics);
                stopEarly = true;
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                metrics.authErrorCount++;
                stopEarly = true;
            } catch (Exception e) {
                long duration = Duration.between(reqStart, Instant.now()).toMillis();
                metrics.totalResponseTime += duration;
                metrics.errorCount++;
            }

            if (stopEarly) break;

            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return metrics;
    }

    private void checkHeaders(HttpHeaders headers, RateLimitTestMetrics metrics) {
        if (headers == null) return;
        String[] limitHeaders = {"X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset", 
                                 "RateLimit-Limit", "RateLimit-Remaining", "RateLimit-Reset", "Retry-After"};
        for (String h : limitHeaders) {
            if (headers.getFirst(h) != null) {
                metrics.rateLimitHeadersObserved = true;
                break;
            }
        }
    }

    private void recordThrottling(int requestNum, HttpHeaders headers, RateLimitTestMetrics metrics) {
        metrics.status429Count++;
        if (metrics.firstThrottledRequest == -1) {
            metrics.firstThrottledRequest = requestNum;
        }
        if (headers != null && headers.getFirst("Retry-After") != null) {
            metrics.retryAfter = headers.getFirst("Retry-After");
        }
    }

    private Finding analyzeMetrics(ApiEndpoint endpoint, RateLimitTestMetrics metrics) {
        if (metrics.authErrorCount > 0) {
            return Finding.builder()
                    .endpointId(endpoint.getId())
                    .path(endpoint.getPath())
                    .httpMethod(endpoint.getHttpMethod())
                    .severity(FindingSeverity.INFO)
                    .title("Endpoint could not be tested because authentication is required")
                    .description("The endpoint returned 401 or 403. Authentication testing will be done later.")
                    .evidence(String.format("Requests sent: %d, Auth errors: %d", metrics.requestsSent, metrics.authErrorCount))
                    .recommendation("Provide valid credentials during authenticated scans.")
                    .build();
        }

        if (metrics.status429Count > 0) {
            return Finding.builder()
                    .endpointId(endpoint.getId())
                    .path(endpoint.getPath())
                    .httpMethod(endpoint.getHttpMethod())
                    .severity(FindingSeverity.INFO)
                    .title("Rate limiting observed")
                    .description("The endpoint returned an HTTP 429 Too Many Requests response during the test.")
                    .evidence(String.format("%d requests sent; HTTP 429 first observed on request %d; Retry-After: %s.", 
                            metrics.requestsSent, metrics.firstThrottledRequest, metrics.retryAfter != null ? metrics.retryAfter : "None"))
                    .recommendation("No action needed. Rate limiting is active.")
                    .build();
        }

        if (metrics.rateLimitHeadersObserved) {
            return Finding.builder()
                    .endpointId(endpoint.getId())
                    .path(endpoint.getPath())
                    .httpMethod(endpoint.getHttpMethod())
                    .severity(FindingSeverity.INFO)
                    .title("Rate-limit headers observed")
                    .description("Rate-limit related headers were observed in the response.")
                    .evidence(String.format("%d requests sent; 429 not hit but headers detected.", metrics.requestsSent))
                    .recommendation("Rate limiting seems configured but threshold not exceeded.")
                    .build();
        }

        int baselineCount = Math.min(5, requestCount / 2);
        if (baselineCount == 0) baselineCount = 1;
        int laterCount = metrics.requestsSent - baselineCount;
        
        if (laterCount > 0 && metrics.baselineTotalTime > 0) {
            double avgBaseline = (double) metrics.baselineTotalTime / baselineCount;
            double avgLater = (double) metrics.laterTotalTime / laterCount;
            
            if (avgBaseline > 0 && (avgLater / avgBaseline) >= latencyIncreaseThreshold) {
                return Finding.builder()
                        .endpointId(endpoint.getId())
                        .path(endpoint.getPath())
                        .httpMethod(endpoint.getHttpMethod())
                        .severity(FindingSeverity.INFO)
                        .title("Possible progressive throttling observed")
                        .description("Response latency increased significantly during the test.")
                        .evidence(String.format("Baseline average: %.2f ms. Later average: %.2f ms.", avgBaseline, avgLater))
                        .recommendation("Verify if progressive throttling is intentional.")
                        .build();
            }
        }

        if (metrics.successfulResponses == metrics.requestsSent) {
            long avg = metrics.requestsSent > 0 ? metrics.totalResponseTime / metrics.requestsSent : 0;
            return Finding.builder()
                    .endpointId(endpoint.getId())
                    .path(endpoint.getPath())
                    .httpMethod(endpoint.getHttpMethod())
                    .severity(FindingSeverity.MEDIUM)
                    .title("No observable rate limiting")
                    .description("The endpoint accepted the controlled request sequence without an observable throttling response or rate-limit signal. This does not prove that the endpoint has no rate limiting because the configured test volume may be below the server's enforcement threshold.")
                    .evidence(String.format("%d requests sent; %d successful responses; 0 HTTP 429 responses; no rate-limit headers observed; average response time: %d ms.", 
                            metrics.requestsSent, metrics.successfulResponses, avg))
                    .recommendation("Review and configure server-side rate limiting appropriate to the endpoint's expected traffic and abuse risk.")
                    .build();
        }

        return null;
    }

    private ScanResult createResult(Long targetId, ScanStatus status, Instant startedAt, int endpointsScanned, int endpointsSkipped, int totalRequestsSent, List<Finding> findings, List<com.mysecure.mysecure.scanner.SkippedEndpoint> skippedEndpoints, String errorMessage) {
        return ScanResult.builder()
                .targetId(targetId)
                .scannerType(ScannerType.RATE_LIMIT)
                .status(status)
                .startedAt(startedAt)
                .completedAt(Instant.now())
                .endpointsScanned(endpointsScanned)
                .endpointsSkipped(endpointsSkipped)
                .totalRequestsSent(totalRequestsSent)
                .findings(findings)
                .skippedEndpoints(skippedEndpoints)
                .errorMessage(errorMessage)
                .build();
    }
}
