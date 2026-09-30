package com.mysecure.mysecure.service;

import com.mysecure.mysecure.dto.TargetConfigurationRequest;
import com.mysecure.mysecure.exception.TargetNotFoundException;
import com.mysecure.mysecure.model.TargetConfiguration;
import com.mysecure.mysecure.repository.TargetConfigurationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TargetConfigurationService {

    private final TargetConfigurationRepository repository;

    public TargetConfiguration createTarget(TargetConfigurationRequest request) {
        TargetConfiguration target = TargetConfiguration.builder()
                .name(request.name())
                .targetUrl(request.targetUrl())
                .openApiUrl(request.openApiUrl())
                .build();
        return repository.save(target);
    }

    public List<TargetConfiguration> getAllTargets() {
        return repository.findAll();
    }

    public TargetConfiguration getTargetById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new TargetNotFoundException("Target with ID " + id + " not found"));
    }

    public void deleteTarget(Long id) {
        if (!repository.existsById(id)) {
            throw new TargetNotFoundException("Target with ID " + id + " not found");
        }
        repository.deleteById(id);
    }
}
