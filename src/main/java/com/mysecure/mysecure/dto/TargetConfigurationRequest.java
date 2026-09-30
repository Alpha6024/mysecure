package com.mysecure.mysecure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TargetConfigurationRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 255)
        String name,

        @NotBlank(message = "Target URL is required")
        @Size(max = 2048)
        String targetUrl,

        @Size(max = 2048)
        String openApiUrl
) {}
