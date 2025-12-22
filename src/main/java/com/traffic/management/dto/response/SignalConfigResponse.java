package com.traffic.management.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 信号灯配置响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalConfigResponse {

    private Long intersectionId;
    private String intersectionName;
    private String mode;
    private String currentPhase;
    private Integer greenDuration;
    private Integer redDuration;
    private Integer yellowDuration;

    // 直行信号灯独立配置
    private Integer straightRedDuration;
    private Integer straightYellowDuration;
    private Integer straightGreenDuration;

    // 转弯信号灯独立配置
    private Integer turnRedDuration;
    private Integer turnYellowDuration;
    private Integer turnGreenDuration;
    private Integer phaseRemaining;
    private Integer cycleTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastAdjustedAt;
}
