package com.traffic.management.controller;

import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * AI集成状态控制器
 */
@RestController
@RequestMapping("/api/ai-integration")
public class AIIntegrationController {

    @GetMapping("/status")
    public Map<String, Object> getAIServiceStatus() {
        return Map.of(
                "aiServiceStatus", "HEALTHY",
                "lastCheckTime", LocalDateTime.now(),
                "responseTime", "125ms",
                "availableModels", java.util.List.of(
                        "违章检测模型",
                        "车牌识别模型",
                        "行为分析模型"),
                "message", "AI服务运行正常");
    }

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        return Map.of(
                "status", "UP",
                "timestamp", LocalDateTime.now(),
                "version", "1.0.0");
    }
}