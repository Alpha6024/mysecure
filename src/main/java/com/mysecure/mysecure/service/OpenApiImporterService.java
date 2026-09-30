package com.mysecure.mysecure.service;

import com.mysecure.mysecure.exception.OpenApiImportException;
import com.mysecure.mysecure.model.ApiEndpoint;
import com.mysecure.mysecure.model.TargetConfiguration;
import com.mysecure.mysecure.repository.ApiEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenApiImporterService {

    private final ApiEndpointRepository endpointRepository;
    private final TargetConfigurationService targetService;
    private final RestTemplate restTemplate;

    private static final Set<String> SUPPORTED_METHODS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace"
    );

    @Transactional
    public List<ApiEndpoint> importFromTarget(Long targetId) {
        TargetConfiguration target = targetService.getTargetById(targetId);

        String openApiUrl = target.getOpenApiUrl();
        if (openApiUrl == null || openApiUrl.isBlank()) {
            throw new OpenApiImportException("Target does not have an openApiUrl configured.");
        }

        validateUrlSecurity(openApiUrl);

        String jsonContent;
        try {
            log.info("Fetching OpenAPI definition from {}", openApiUrl);
            jsonContent = restTemplate.getForObject(openApiUrl, String.class);
        } catch (RestClientException e) {
            log.error("Failed to fetch OpenAPI document", e);
            throw new OpenApiImportException("Failed to fetch OpenAPI document: " + e.getMessage(), e);
        }

        if (jsonContent == null || jsonContent.isBlank()) {
            throw new OpenApiImportException("Received empty response from OpenAPI URL");
        }

        List<ApiEndpoint> parsedEndpoints = parseOpenApi(jsonContent, targetId);

        // Delete existing endpoints to prevent duplicates on re-import
        endpointRepository.deleteByTargetId(targetId);

        // Save new endpoints
        return endpointRepository.saveAll(parsedEndpoints);
    }

    private void validateUrlSecurity(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new OpenApiImportException("Unsupported protocol. Only HTTP and HTTPS are allowed.");
            }
        } catch (URISyntaxException e) {
            throw new OpenApiImportException("Malformed OpenAPI URL: " + url, e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ApiEndpoint> parseOpenApi(String json, Long targetId) {
        List<ApiEndpoint> endpoints = new ArrayList<>();
        try {
            JsonParser parser = JsonParserFactory.getJsonParser();
            Map<String, Object> root = parser.parseMap(json);
            
            Object openapiObj = root.get("openapi");
            if (openapiObj == null || !openapiObj.toString().startsWith("3.")) {
                throw new OpenApiImportException("Unsupported OpenAPI version. Only OpenAPI 3.x is supported.");
            }

            Object pathsObj = root.get("paths");
            if (!(pathsObj instanceof Map)) {
                log.warn("No 'paths' object found in OpenAPI definition");
                return endpoints;
            }

            Map<String, Object> pathsNode = (Map<String, Object>) pathsObj;
            for (Map.Entry<String, Object> pathEntry : pathsNode.entrySet()) {
                String path = pathEntry.getKey();
                if (!(pathEntry.getValue() instanceof Map)) continue;
                
                Map<String, Object> methodsNode = (Map<String, Object>) pathEntry.getValue();

                for (Map.Entry<String, Object> methodEntry : methodsNode.entrySet()) {
                    String method = methodEntry.getKey().toLowerCase();

                    if (SUPPORTED_METHODS.contains(method)) {
                        if (!(methodEntry.getValue() instanceof Map)) continue;
                        Map<String, Object> operationNode = (Map<String, Object>) methodEntry.getValue();
                        
                        String operationId = operationNode.containsKey("operationId") ? operationNode.get("operationId").toString() : null;
                        String summary = operationNode.containsKey("summary") ? operationNode.get("summary").toString() : null;
                        String description = operationNode.containsKey("description") ? operationNode.get("description").toString() : null;
                        
                        Boolean authRequired = null;
                        if (operationNode.containsKey("security")) {
                            Object sec = operationNode.get("security");
                            if (sec instanceof List) {
                                authRequired = !((List<?>) sec).isEmpty();
                            }
                        } else if (root.containsKey("security")) {
                            Object sec = root.get("security");
                            if (sec instanceof List) {
                                authRequired = !((List<?>) sec).isEmpty();
                            }
                        }

                        ApiEndpoint endpoint = ApiEndpoint.builder()
                                .targetId(targetId)
                                .path(path)
                                .httpMethod(method.toUpperCase())
                                .operationId(operationId)
                                .summary(summary)
                                .description(description)
                                .authenticationRequired(authRequired)
                                .build();
                                
                        endpoints.add(endpoint);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse OpenAPI JSON", e);
            throw new OpenApiImportException("Failed to parse OpenAPI JSON: " + e.getMessage(), e);
        }
        
        return endpoints;
    }
}
