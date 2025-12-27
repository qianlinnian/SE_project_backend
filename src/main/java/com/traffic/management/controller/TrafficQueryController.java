package com.traffic.management.controller;

import com.traffic.management.dto.traffic.TrafficDataDTO;
import com.traffic.management.entity.TrafficFlowRecord;
import com.traffic.management.service.TrafficDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 供前端查询交通数据的接口
 * 替代原 TrafficMonitorController 的部分功能
 */
@RestController
@RequestMapping("/api/traffic")
public class TrafficQueryController {

    @Autowired
    private TrafficDataService trafficDataService;

    // 1.2 前端获取最新交通数据
    @GetMapping("/latest")
    public ResponseEntity<?> getLatestTraffic() {
        TrafficDataDTO data = trafficDataService.getLatestTrafficData();
        if (data == null) {
            // 如果 Redis 空，返回空结构防止前端报错
            return ResponseEntity.ok(Map.of("code", 200, "message", "No data yet", "data", Map.of()));
        }
        return ResponseEntity.ok(Map.of(
            "code", 200, 
            "message", "success", 
            "data", data
        ));
    }

    // 1.3 获取交通数据历史记录
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start_time,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end_time,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        
        List<TrafficFlowRecord> records = trafficDataService.getHistory(start_time, end_time, limit);
        
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "message", "success",
            "data", Map.of("records", records)
        ));
    }
}