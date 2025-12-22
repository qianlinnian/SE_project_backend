package com.traffic.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 信号灯配置实体
 */
@Entity
@Table(name = "signal_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intersection_id", nullable = false, unique = true)
    private Long intersectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_mode", nullable = false, length = 20)
    private SignalMode signalMode;

    @Column(name = "green_duration", nullable = false)
    private Integer greenDuration;

    @Column(name = "straight_red_duration")
    private Integer straightRedDuration;

    @Column(name = "straight_yellow_duration")
    private Integer straightYellowDuration;

    @Column(name = "straight_green_duration")
    private Integer straightGreenDuration;

    @Column(name = "turn_red_duration")
    private Integer turnRedDuration;

    @Column(name = "turn_yellow_duration")
    private Integer turnYellowDuration;

    @Column(name = "turn_green_duration")
    private Integer turnGreenDuration;

    @Column(name = "red_duration", nullable = false)
    private Integer redDuration;

    @Column(name = "yellow_duration", nullable = false)
    private Integer yellowDuration;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", nullable = false, length = 10)
    private SignalPhase currentPhase;

    @Column(name = "phase_remaining", nullable = false)
    private Integer phaseRemaining;

    @Column(name = "cycle_time", nullable = false)
    private Integer cycleTime;

    @Column(name = "last_adjusted_at")
    private LocalDateTime lastAdjustedAt;

    @Column(name = "last_adjusted_by")
    private Long lastAdjustedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (signalMode == null) {
            signalMode = SignalMode.FIXED;
        }
        if (currentPhase == null) {
            currentPhase = SignalPhase.STRAIGHT_GREEN;
        }
        // 初始化直行信号灯时长
        if (straightRedDuration == null) {
            straightRedDuration = redDuration;
        }
        if (straightYellowDuration == null) {
            straightYellowDuration = yellowDuration;
        }
        if (straightGreenDuration == null && greenDuration != null) {
            straightGreenDuration = (int) (greenDuration * 0.6); // 60%作为直行绿灯
        }

        // 初始化转弯信号灯时长
        if (turnRedDuration == null) {
            turnRedDuration = straightRedDuration + straightGreenDuration + straightYellowDuration;
        }
        if (turnYellowDuration == null) {
            turnYellowDuration = 3; // 转弯黄灯固定3秒
        }
        if (turnGreenDuration == null && greenDuration != null && straightGreenDuration != null) {
            turnGreenDuration = greenDuration - straightGreenDuration; // 剩余作为转弯绿灯
        }
        if (yellowDuration == null) {
            yellowDuration = 3;
        }
        calculateCycleTime();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        calculateCycleTime();
    }

    /**
     * 计算完整周期时长（直行和转弯独立周期的最大值）
     */
    private void calculateCycleTime() {
        // 计算直行周期时长
        int straightCycle = (straightRedDuration != null ? straightRedDuration : 0) +
                (straightYellowDuration != null ? straightYellowDuration : 0) +
                (straightGreenDuration != null ? straightGreenDuration : 0);

        // 计算转弯周期时长
        int turnCycle = (turnRedDuration != null ? turnRedDuration : 0) +
                (turnYellowDuration != null ? turnYellowDuration : 0) +
                (turnGreenDuration != null ? turnGreenDuration : 0);

        // 取两个周期的最大值作为完整周期
        this.cycleTime = Math.max(straightCycle, turnCycle);

        // 向后兼容：如果没有设置新字段，使用原来的逻辑
        if (this.cycleTime == 0 && greenDuration != null) {
            this.cycleTime = greenDuration + redDuration + yellowDuration;
        }
    }

    /**
     * 信号灯模式枚举
     */
    public enum SignalMode {
        FIXED, // 固定配时
        INTELLIGENT, // 智能调控
        MANUAL // 人工干预
    }

    /**
     * 信号灯阶段枚举
     */
    public enum SignalPhase {
        RED, // 保持向后兼容
        YELLOW, // 保持向后兼容
        GREEN, // 保持向后兼容
        STRAIGHT_RED, // 直行红灯
        STRAIGHT_YELLOW, // 直行黄灯
        STRAIGHT_GREEN, // 直行绿灯
        TURN_RED, // 转弯红灯
        TURN_YELLOW, // 转弯黄灯
        TURN_GREEN // 转弯绿灯
    }
}
