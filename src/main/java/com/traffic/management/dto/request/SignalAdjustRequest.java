package com.traffic.management.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 信号灯调整请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalAdjustRequest {

    @NotBlank(message = "信号灯模式不能为空")
    private String mode; // FIXED, INTELLIGENT, MANUAL

    @NotNull(message = "绿灯时长不能为空")
    @Min(value = 10, message = "绿灯时长不能小于10秒")
    private Integer greenDuration;

    @Size(max = 255, message = "调整原因长度不能超过255")
    private String reason;
}
