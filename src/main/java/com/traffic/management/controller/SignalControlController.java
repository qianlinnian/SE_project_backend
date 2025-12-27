package com.traffic.management.controller;

import com.traffic.management.dto.traffic.ControlCommandRequest;
import com.traffic.management.entity.SignalConfig;
import com.traffic.management.repository.SignalConfigRepository;
import com.traffic.management.service.AIIntegrationService;
import com.traffic.management.service.SignalControlService;
import com.traffic.management.service.TrafficDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 前端控制指令接口
 */
@RestController
@RequestMapping("/api/control")
public class SignalControlController {

    @Autowired
    private SignalControlService signalControlService;

    @Autowired
    private SignalConfigRepository signalConfigRepository;
    
    @Autowired
    private AIIntegrationService aiIntegrationService;

    @Autowired
    private TrafficDataService trafficDataService;

    // 2.1 切换控制模式
    @PostMapping("/mode")
    public ResponseEntity<?> switchControlMode(@RequestBody ControlCommandRequest request) {
        try {
            // 真实场景：应从 SecurityContext 获取当前用户 ID
            // 这里假设 1L 为管理员，后续请替换为 UserContext.getUserId()
            Long currentUserId = 1L; 
            
            if (!"ai".equals(request.getMode()) && !"fixed".equals(request.getMode())) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Invalid mode: must be 'ai' or 'fixed'"));
            }

            // 执行核心业务：转发 LLM + 记录日志 + 更新 DB
            signalControlService.switchMode(request.getMode(), currentUserId);

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", Map.of(
                    "current_mode", request.getMode(),
                    "switched_at", LocalDateTime.now()
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", e.getMessage()));
        }
    }

    // 2.2 设置固定配时周期
    @PostMapping("/fixed-duration")
    public ResponseEntity<?> setFixedDuration(@RequestBody ControlCommandRequest request) {
        try {
            Long currentUserId = 1L;
            
            if (request.getDuration() == null || request.getDuration() < 10 || request.getDuration() > 120) {
                 return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Duration must be between 10 and 120"));
            }

            signalControlService.setFixedDuration(request.getDuration(), currentUserId);

            return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "success",
                "data", Map.of(
                    "fixed_phase_duration", request.getDuration()
                )
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", e.getMessage()));
        }
    }
    
    // 2.3 获取当前控制状态 (真实逻辑)
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        // 1. 从数据库获取真实的路口配置 (以 ID=1 的路口为参考)
        Optional<SignalConfig> configOpt = signalConfigRepository.findById(1L);
        
        String currentMode = "unknown";
        int fixedDuration = 30; // 默认值

        if (configOpt.isPresent()) {
            SignalConfig config = configOpt.get();
            // 映射 DB 枚举到 API 字符串
            if ("INTELLIGENT".equals(config.getSignalMode())) {
                currentMode = "ai";
            } else if ("FIXED".equals(config.getSignalMode())) {
                currentMode = "fixed";
            } else {
                currentMode = "manual";
            }
            // 假设 cycle_time 或 green_duration 作为固定配时参考
            fixedDuration = config.getGreenDuration(); 
        }

        // 2. 检查 LLM 服务健康状态
        boolean llmConnected = aiIntegrationService.checkAIServiceHealth();

        // 3. 检查仿真是否在运行 (判断依据：最近 10 秒内是否收到过交通数据)
        LocalDateTime lastDataTime = trafficDataService.getLastDataReceivedTime();
        boolean simulationRunning = false;
        if (lastDataTime != null) {
            long secondsDiff = Duration.between(lastDataTime, LocalDateTime.now()).getSeconds();
            simulationRunning = secondsDiff < 10;
        }

        return ResponseEntity.ok(Map.of(
            "code", 200,
            "message", "success",
            "data", Map.of(
                "control_mode", currentMode,
                "fixed_phase_duration", fixedDuration,
                "llm_server_connected", llmConnected,
                "simulation_running", simulationRunning
            )
        ));
    }
}