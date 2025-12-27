package com.traffic.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交通相位配置实体 - 定义路口整体的交通相位时序
 */
@Entity
@Table(name = "traffic_phases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficPhase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intersection_id", nullable = false)
    private Long intersectionId;

    @Column(name = "phase_name", nullable = false, length = 50)
    private String phaseName;

    @Column(name = "phase_sequence", nullable = false)
    private Integer phaseSequence;

    // 东向行为参与标识
    @Column(name = "east_straight", nullable = false)
    private Boolean eastStraight;

    @Column(name = "east_left_turn", nullable = false)
    private Boolean eastLeftTurn;

    @Column(name = "east_right_turn", nullable = false)
    private Boolean eastRightTurn;

    // 南向行为参与标识
    @Column(name = "south_straight", nullable = false)
    private Boolean southStraight;

    @Column(name = "south_left_turn", nullable = false)
    private Boolean southLeftTurn;

    @Column(name = "south_right_turn", nullable = false)
    private Boolean southRightTurn;

    // 西向行为参与标识
    @Column(name = "west_straight", nullable = false)
    private Boolean westStraight;

    @Column(name = "west_left_turn", nullable = false)
    private Boolean westLeftTurn;

    @Column(name = "west_right_turn", nullable = false)
    private Boolean westRightTurn;

    // 北向行为参与标识
    @Column(name = "north_straight", nullable = false)
    private Boolean northStraight;

    @Column(name = "north_left_turn", nullable = false)
    private Boolean northLeftTurn;

    @Column(name = "north_right_turn", nullable = false)
    private Boolean northRightTurn;

    // 相位时长配置
    @Column(name = "green_duration", nullable = false)
    private Integer greenDuration;

    @Column(name = "yellow_duration", nullable = false)
    private Integer yellowDuration;

    @Column(name = "all_red_duration", nullable = false)
    private Integer allRedDuration;

    // 相位状态
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false)
    private PhaseStatus currentStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
        if (currentStatus == null) {
            currentStatus = PhaseStatus.WAITING;
        }
        if (eastStraight == null)
            eastStraight = false;
        if (eastLeftTurn == null)
            eastLeftTurn = false;
        if (eastRightTurn == null)
            eastRightTurn = false;
        if (southStraight == null)
            southStraight = false;
        if (southLeftTurn == null)
            southLeftTurn = false;
        if (southRightTurn == null)
            southRightTurn = false;
        if (westStraight == null)
            westStraight = false;
        if (westLeftTurn == null)
            westLeftTurn = false;
        if (westRightTurn == null)
            westRightTurn = false;
        if (northStraight == null)
            northStraight = false;
        if (northLeftTurn == null)
            northLeftTurn = false;
        if (northRightTurn == null)
            northRightTurn = false;
        if (yellowDuration == null)
            yellowDuration = 3;
        if (allRedDuration == null)
            allRedDuration = 2;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 相位状态枚举
     */
    public enum PhaseStatus {
        WAITING, // 等待中
        GREEN, // 绿灯阶段
        YELLOW, // 黄灯阶段
        ALL_RED // 全红阶段
    }
}