package com.mysecure.mysecure.scanner;

import lombok.Builder;

@Builder
public record Finding(
        Long endpointId,
        String path,
        String httpMethod,
        FindingSeverity severity,
        String title,
        String description,
        String evidence,
        String recommendation
) {}
