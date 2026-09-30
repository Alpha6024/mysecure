package com.mysecure.mysecure.controller;

import com.mysecure.mysecure.dto.OpenApiImportResponse;
import com.mysecure.mysecure.model.ApiEndpoint;
import com.mysecure.mysecure.repository.ApiEndpointRepository;
import com.mysecure.mysecure.service.OpenApiImporterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/targets/{targetId}")
@RequiredArgsConstructor
public class OpenApiImportController {

    private final OpenApiImporterService importerService;
    private final ApiEndpointRepository endpointRepository;

    @PostMapping("/import-openapi")
    public ResponseEntity<OpenApiImportResponse> importOpenApi(@PathVariable Long targetId) {
        List<ApiEndpoint> importedEndpoints = importerService.importFromTarget(targetId);
        OpenApiImportResponse response = new OpenApiImportResponse(
                targetId,
                importedEndpoints.size(),
                importedEndpoints
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/endpoints")
    public ResponseEntity<List<ApiEndpoint>> getEndpoints(@PathVariable Long targetId) {
        List<ApiEndpoint> endpoints = endpointRepository.findByTargetId(targetId);
        return ResponseEntity.ok(endpoints);
    }
}
