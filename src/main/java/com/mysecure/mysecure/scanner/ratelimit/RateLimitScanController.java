package com.mysecure.mysecure.scanner.ratelimit;

import com.mysecure.mysecure.scanner.ScanRequest;
import com.mysecure.mysecure.scanner.ScanResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scanners/rate-limit")
@RequiredArgsConstructor
public class RateLimitScanController {

    private final RateLimitScanner rateLimitScanner;

    @PostMapping
    public ResponseEntity<ScanResult> startScan(@RequestBody ScanRequest request) {
        if (request.targetId() == null) {
            return ResponseEntity.badRequest().build();
        }
        ScanResult result = rateLimitScanner.scan(request);
        return ResponseEntity.ok(result);
    }
}
