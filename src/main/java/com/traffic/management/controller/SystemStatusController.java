package com.traffic.management.controller;

import com.traffic.management.service.AIIntegrationService;
import com.traffic.management.service.TrafficDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 系统状态接口
 */
@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    @Autowired
    private TrafficDataService trafficDataService;

    @Autowired
    private AIIntegrationService aiIntegrationService;

    // 3.1 获取系统状态
    @GetMapping("/status")
    public ResponseEntity<?> getSystemStatus() {
        // 1. 获取后端运行时间
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeSeconds = uptimeMillis / 1000;

        // 2. 获取 LLM 连接状态
        boolean isLlmHealthy = aiIntegrationService.checkAIServiceHealth();
        String llmStatus = isLlmHealthy ? "connected" : "disconnected";

        // 3. 获取最后接收数据时间
        LocalDateTime lastDataTime = trafficDataService.getLastDataReceivedTime();
        String lastDataStr = lastDataTime != null ? lastDataTime.toString() : "never";

        // 4. 判断后端状态 (能调通接口即为 running)
        String backendStatus = "running";

        return ResponseEntity.ok(Map.of(
            "code", 200,
            "message", "success",
            "data", Map.of(
                "backend_status", backendStatus,
                "llm_server_status", llmStatus,
                "last_data_received", lastDataStr,
                "uptime_seconds", uptimeSeconds
            )
        ));
    }
}