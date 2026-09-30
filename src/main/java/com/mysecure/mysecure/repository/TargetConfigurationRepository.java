package com.mysecure.mysecure.repository;

import com.mysecure.mysecure.model.TargetConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TargetConfigurationRepository extends JpaRepository<TargetConfiguration, Long> {
}
