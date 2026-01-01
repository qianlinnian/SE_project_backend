package com.traffic.management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.management.dto.traffic.ControlCommandRequest;
import com.traffic.management.entity.SignalLog;
import com.traffic.management.handler.TrafficDataWebSocketHandler;
import com.traffic.management.repository.SignalLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 负责处理信号灯控制指令、日志记录及与 LLM 的通信
 */
@Slf4j
@Service
public class SignalControlService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SignalLogRepository signalLogRepository;
    
    @Autowired
    private TrafficDataWebSocketHandler webSocketHandler;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ai.service.base-url:https://u836978-a67f-943bbb9f.westc.gpuhub.com:8443}")
    private String aiServerUrl;
    
    @Value("${ai.service.control-endpoint:/api/control}")
    private String controlEndpoint;

    /**
     * 切换控制模式
     */
    public void switchMode(String mode, Long operatorId) throws Exception {
        // 1. 构造发给 LLM 的请求体
        Map<String, Object> payload = new HashMap<>();
        payload.put("command", "set_mode");
        payload.put("mode", mode);

        // 2. 发送请求给 LLM
        sendToLLM(payload);

        // 3. 记录日志
        logAction(null, SignalLog.ActionType.MODE_CHANGE, 
                Map.of("mode", "unknown"), // 简化，实际需查询当前状态
                Map.of("mode", mode), 
                operatorId, "User switched control mode");
                
        // 4. 通过 WebSocket 广播模式变更通知 (可选，根据文档 4.1)
        broadcastModeChange(mode);
    }

    /**
     * 设置固定配时时长
     */
    public void setFixedDuration(Integer duration, Long operatorId) throws Exception {
        // 1. 构造请求体
        Map<String, Object> payload = new HashMap<>();
        payload.put("command", "set_fixed_duration");
        payload.put("duration", duration);

        // 2. 发送
        sendToLLM(payload);

        // 3. 日志
        logAction(null, SignalLog.ActionType.MANUAL_OVERRIDE, 
                Map.of(), 
                Map.of("duration", duration), 
                operatorId, "User set fixed duration");
    }

    /**
     * 核心方法：发送 HTTP POST 给 LLM
     */
    private void sendToLLM(Map<String, Object> payload) throws Exception {
        String url = aiServerUrl + controlEndpoint;
        log.info("🔄 准备发送LLM控制命令 - URL: {}, Payload: {}", url, payload);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            // RestTemplate 已配置超时，这里直接调用
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("❌ LLM返回非200状态码: {}", response.getStatusCode());
                throw new RuntimeException("LLM Server returned " + response.getStatusCode());
            }
            log.info("✅ LLM控制命令发送成功 - 响应: {}", response.getBody());
        } catch (Exception e) {
            log.error("❌ 发送LLM控制命令失败 - URL: {}, 错误: {}", url, e.getMessage(), e);
            throw new RuntimeException("Failed to communicate with LLM Server: " + e.getMessage());
        }
    }

    /**
     * 记录操作日志
     */
    private void logAction(Long intersectionId, String actionType, Map<String, Object> oldConfig, 
                          Map<String, Object> newConfig, Long operatorId, String reason) {
        try {
            SignalLog logEntry = SignalLog.builder()
                    // 如果是全局命令，intersectionId 可能为 0 或 null，视数据库约束而定
                    // 这里假设 0 代表全局
                    .intersectionId(intersectionId != null ? intersectionId : 0L) 
                    .actionType(actionType)
                    .oldConfig(oldConfig)
                    .newConfig(newConfig)
                    .operatorId(operatorId)
                    .reason(reason)
                    .ipAddress("127.0.0.1") // 简化，实际应从 RequestContext 获取
                    .build();
            
            signalLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to save signal log", e);
            // 日志失败不应阻断控制流程
        }
    }
    
    private void broadcastModeChange(String newMode) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", "mode_changed");
            Map<String, Object> data = new HashMap<>();
            data.put("new_mode", newMode);
            data.put("changed_at", LocalDateTime.now().toString());
            msg.put("data", data);
            
            webSocketHandler.broadcast(objectMapper.writeValueAsString(msg));
        } catch(Exception e) {
            log.error("Broadcast failed", e);
        }
    }
}