package com.mysecure.mysecure.controller;

import com.mysecure.mysecure.dto.TargetConfigurationRequest;
import com.mysecure.mysecure.model.TargetConfiguration;
import com.mysecure.mysecure.service.TargetConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/targets")
@RequiredArgsConstructor
public class TargetConfigurationController {

    private final TargetConfigurationService targetConfigurationService;

    @PostMapping
    public ResponseEntity<TargetConfiguration> createTarget(@Valid @RequestBody TargetConfigurationRequest request) {
        TargetConfiguration target = targetConfigurationService.createTarget(request);
        return new ResponseEntity<>(target, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TargetConfiguration>> getAllTargets() {
        List<TargetConfiguration> targets = targetConfigurationService.getAllTargets();
        return ResponseEntity.ok(targets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TargetConfiguration> getTargetById(@PathVariable Long id) {
        TargetConfiguration target = targetConfigurationService.getTargetById(id);
        return ResponseEntity.ok(target);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTarget(@PathVariable Long id) {
        targetConfigurationService.deleteTarget(id);
        return ResponseEntity.noContent().build();
    }
}
