package com.traffic.management.controller;

import com.traffic.management.dto.traffic.TrafficDataDTO;
import com.traffic.management.service.TrafficDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 专门用于接收 LLM 服务器推送的数据
 */
@Slf4j
@RestController
@RequestMapping("/api/traffic")
public class TrafficIngestionController {

    @Autowired
    private TrafficDataService trafficDataService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> receiveTrafficData(@RequestBody TrafficDataDTO dataDTO) {
        // 异步处理，尽快返回 200 给 LLM，避免阻塞对方
        trafficDataService.processIngressData(dataDTO);
        // ===== 添加日志 =====
        log.info("========================================");
        log.info("收到数据:");
        log.info("  类型: {}", dataDTO.getType());
        log.info("  步数: {}", dataDTO.getStep());
        log.info("  时间戳: {}", dataDTO.getTimestamp());
        log.info("  路网: {}", dataDTO.getRoadnet());
        log.info("  路口数: {}", dataDTO.getTotalIntersections());
        log.info("========================================");
        if (dataDTO.getIntersections() != null && !dataDTO.getIntersections().isEmpty()) {
        log.info("  第一个路口: id={}, signal={}, queue={}", 
                 dataDTO.getIntersections().get(0).getId(),
                 dataDTO.getIntersections().get(0).getSignalPhase(),
                 dataDTO.getIntersections().get(0).getQueueLength());
        }
        log.info("========================================");
        // ===================================

        return ResponseEntity.ok(Map.of(
            "code", 200,
            "message", "success"
        ));
    }
}