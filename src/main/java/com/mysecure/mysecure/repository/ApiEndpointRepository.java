package com.mysecure.mysecure.repository;

import com.mysecure.mysecure.model.ApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {

    List<ApiEndpoint> findByTargetId(Long targetId);

    void deleteByTargetId(Long targetId);
}
