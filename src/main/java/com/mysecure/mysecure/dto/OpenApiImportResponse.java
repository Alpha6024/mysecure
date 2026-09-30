package com.mysecure.mysecure.dto;

import com.mysecure.mysecure.model.ApiEndpoint;
import java.util.List;

public record OpenApiImportResponse(
        Long targetId,
        int importedCount,
        List<ApiEndpoint> endpoints
) {}
