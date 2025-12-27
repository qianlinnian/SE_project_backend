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
        
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "message", "success"
        ));
    }
}