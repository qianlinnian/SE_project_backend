package com.traffic.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 路口方向实体 - 每个路口的四个方向配置
 */
@Entity
@Table(name = "intersection_directions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntersectionDirection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intersection_id", nullable = false)
    private Long intersectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private Direction direction;

    @Column(name = "direction_name", nullable = false, length = 50)
    private String directionName;

    @Column(name = "lane_count", nullable = false)
    private Integer laneCount;

    @Column(name = "has_turn_lane", nullable = false)
    private Boolean hasTurnLane;

    // 直行信号灯配置
    @Column(name = "straight_red_duration", nullable = false)
    private Integer straightRedDuration;

    @Column(name = "straight_yellow_duration", nullable = false)
    private Integer straightYellowDuration;

    @Column(name = "straight_green_duration", nullable = false)
    private Integer straightGreenDuration;

    // 左转信号灯配置
    @Column(name = "left_turn_red_duration", nullable = false)
    private Integer leftTurnRedDuration;

    @Column(name = "left_turn_yellow_duration", nullable = false)
    private Integer leftTurnYellowDuration;

    @Column(name = "left_turn_green_duration", nullable = false)
    private Integer leftTurnGreenDuration;

    // 右转信号灯配置
    @Column(name = "right_turn_red_duration", nullable = false)
    private Integer rightTurnRedDuration;

    @Column(name = "right_turn_yellow_duration", nullable = false)
    private Integer rightTurnYellowDuration;

    @Column(name = "right_turn_green_duration", nullable = false)
    private Integer rightTurnGreenDuration;

    // 当前信号灯状态
    @Enumerated(EnumType.STRING)
    @Column(name = "current_straight_phase", nullable = false)
    private LightPhase currentStraightPhase;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_left_turn_phase", nullable = false)
    private LightPhase currentLeftTurnPhase;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_right_turn_phase", nullable = false)
    private LightPhase currentRightTurnPhase;

    // 相位剩余时间
    @Column(name = "straight_phase_remaining", nullable = false)
    private Integer straightPhaseRemaining;

    @Column(name = "left_turn_phase_remaining", nullable = false)
    private Integer leftTurnPhaseRemaining;

    @Column(name = "right_turn_phase_remaining", nullable = false)
    private Integer rightTurnPhaseRemaining;

    // 优先级和权重
    @Column(name = "priority_level", nullable = false)
    private Integer priorityLevel;

    @Column(name = "traffic_weight", nullable = false, precision = 3, scale = 2)
    private BigDecimal trafficWeight;

    // 设备关联
    @Column(name = "camera_id", length = 50)
    private String cameraId;

    @Column(name = "detector_id", length = 50)
    private String detectorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (currentStraightPhase == null) {
            currentStraightPhase = LightPhase.RED;
        }
        if (currentLeftTurnPhase == null) {
            currentLeftTurnPhase = LightPhase.RED;
        }
        if (currentRightTurnPhase == null) {
            currentRightTurnPhase = LightPhase.RED;
        }
        if (straightPhaseRemaining == null) {
            straightPhaseRemaining = 0;
        }
        if (leftTurnPhaseRemaining == null) {
            leftTurnPhaseRemaining = 0;
        }
        if (rightTurnPhaseRemaining == null) {
            rightTurnPhaseRemaining = 0;
        }
        if (priorityLevel == null) {
            priorityLevel = 1;
        }
        if (trafficWeight == null) {
            trafficWeight = new BigDecimal("1.00");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
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
     * 信号灯相位枚举
     */
    public enum LightPhase {
        RED,    // 红灯
        YELLOW, // 黄灯
        GREEN   // 绿灯
    }
}