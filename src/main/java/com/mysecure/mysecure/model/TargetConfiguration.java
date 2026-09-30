package com.mysecure.mysecure.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "target_configurations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TargetConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2048)
    private String targetUrl;

    @Column(length = 2048)
    private String openApiUrl;
}