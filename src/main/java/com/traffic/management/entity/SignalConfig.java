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
            currentPhase = SignalPhase.GREEN;
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
     * 计算完整周期时长
     */
    private void calculateCycleTime() {
        this.cycleTime = greenDuration + redDuration + yellowDuration;
    }

    /**
     * 信号灯模式枚举
     */
    public enum SignalMode {
        FIXED,          // 固定配时
        INTELLIGENT,    // 智能调控
        MANUAL          // 人工干预
    }

    /**
     * 信号灯阶段枚举
     */
    public enum SignalPhase {
        RED,
        YELLOW,
        GREEN
    }
}
