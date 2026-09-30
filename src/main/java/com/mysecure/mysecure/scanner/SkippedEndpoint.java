package com.mysecure.mysecure.scanner;

import lombok.Builder;

@Builder
public record SkippedEndpoint(
        Long endpointId,
        String path,
        String httpMethod,
        String reason
) {}
