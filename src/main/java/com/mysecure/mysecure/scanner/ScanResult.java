package com.mysecure.mysecure.scanner;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record ScanResult(
        Long targetId,
        ScannerType scannerType,
        ScanStatus status,
        Instant startedAt,
        Instant completedAt,
        Integer endpointsScanned,
        Integer endpointsSkipped,
        Integer totalRequestsSent,
        List<Finding> findings,
        List<SkippedEndpoint> skippedEndpoints,
        String errorMessage
) {}
