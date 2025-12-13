package com.traffic.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "intersections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Intersection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "device_ip", length = 45)
    private String deviceIp;

    @Column(name = "device_id", length = 50)
    private String deviceId;

    @Column(name = "current_status")
    @Enumerated(EnumType.STRING)
    private IntersectionStatus currentStatus;

    @Column(name = "capacity_level")
    private Integer capacityLevel;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (currentStatus == null) {
            currentStatus = IntersectionStatus.NORMAL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum IntersectionStatus {
        NORMAL, CONGESTED, OFFLINE
    }
}
