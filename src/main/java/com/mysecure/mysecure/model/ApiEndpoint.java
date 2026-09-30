package com.mysecure.mysecure.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "api_endpoints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 2048)
    private String path;

    @Column(nullable = false, length = 10)
    private String httpMethod;

    @Column(length = 255)
    private String operationId;

    @Column(length = 1024)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Boolean authenticationRequired;
}
