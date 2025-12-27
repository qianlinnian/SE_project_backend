package com.traffic.management.dto.traffic;

import lombok.Data;

/**
 * 前端控制指令请求体
 */
@Data
public class ControlCommandRequest {
    // 模式切换: "ai" 或 "fixed"
    private String mode;
    
    // 固定配时时长 (秒)
    private Integer duration;
}