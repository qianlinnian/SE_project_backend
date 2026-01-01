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

    @Enumerated(EnumType.STRING)
    @Column(name = "direction")
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "turn_type")
    private TurnType turnType;

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    /**
     * 车辆类型 (car, bus, truck, bicycle, motorcycle, train)
     */
    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

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
        RED_LIGHT, // 闯红灯
        WRONG_WAY, // 逆行
        CROSS_SOLID_LINE, // 跨实线
        ILLEGAL_TURN, // 违法转弯
        WAITING_AREA_RED_ENTRY, // 待转区红灯进入
        WAITING_AREA_ILLEGAL_EXIT, // 待转区非法驶离
        SPEEDING, // 超速
        PARKING_VIOLATION, // 违章停车
        OTHER // 其他
    }

    public enum ViolationStatus {
        PENDING, CONFIRMED, REJECTED
    }

    public enum AppealStatus {
        NO_APPEAL, APPEALING, APPEALED
    }

    /**
     * 方向枚举
     */
    public enum Direction {
        EAST("东"),
        SOUTH("南"),
        WEST("西"),
        NORTH("北");

        private final String chineseName;

        Direction(String chineseName) {
            this.chineseName = chineseName;
        }

        public String getChineseName() {
            return chineseName;
        }
    }

    /**
     * 转弯类型枚举
     */
    public enum TurnType {
        STRAIGHT("直行"),
        LEFT_TURN("左转"),
        RIGHT_TURN("右转"),
        U_TURN("掉头");

        private final String chineseName;

        TurnType(String chineseName) {
            this.chineseName = chineseName;
        }

        public String getChineseName() {
            return chineseName;
        }
    }
}
