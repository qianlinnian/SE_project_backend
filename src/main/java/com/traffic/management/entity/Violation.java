package com.traffic.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "violations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Violation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intersection_id", nullable = false)
    private Long intersectionId;

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Column(name = "violation_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ViolationType violationType;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "ai_confidence")
    private Float aiConfidence;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ViolationStatus status;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "penalty_amount", precision = 10, scale = 2)
    private BigDecimal penaltyAmount;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "appeal_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AppealStatus appealStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = ViolationStatus.PENDING;
        }
        if (appealStatus == null) {
            appealStatus = AppealStatus.NO_APPEAL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ViolationType {
        RED_LIGHT, WRONG_WAY, ILLEGAL_LANE, SPEEDING, PARKING_VIOLATION, OTHER
    }

    public enum ViolationStatus {
        PENDING, CONFIRMED, REJECTED
    }

    public enum AppealStatus {
        NO_APPEAL, APPEALING, APPEALED
    }
}
