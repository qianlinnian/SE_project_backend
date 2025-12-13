package com.traffic.management.controller;

import com.traffic.management.handler.AlertWebSocketHandler;
import com.traffic.management.service.ViolationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ViolationController {

    @Autowired
    private ViolationService violationService;

    @Autowired
    private AlertWebSocketHandler alertWebSocketHandler;

    // POST /api/violations/report : 上报违章行为
    @PostMapping("/violations/report")
    public Map<String, Object> reportViolation(@RequestBody Map<String, Object> violation) {
        var savedViolation = violationService.reportViolation(violation);

        // 发送实时警报
        String violationType = violation.containsKey("type") ? violation.get("type").toString() : 
                              (violation.containsKey("violationType") ? violation.get("violationType").toString() : "UNKNOWN");
        String intersectionId = violation.containsKey("intersectionId") ? violation.get("intersectionId").toString() : "UNKNOWN";
        String alertMessage = "New violation detected: " + violationType +
                " at intersection " + intersectionId;
        alertWebSocketHandler.sendAlert(alertMessage);

        return Map.of(
                "id", savedViolation.getId(),
                "message", "Violation reported successfully");
    }

    // GET /api/violations : 查询违章记录列表
    @GetMapping("/violations")
    public List<Map<String, Object>> getViolations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return violationService.getViolations(page, size);
    }

    // GET /api/violations/{id} : 查看单条违章详情
    @GetMapping("/violations/{id}")
    public Map<Object, Object> getViolationDetail(@PathVariable String id) {
        return violationService.getViolationDetail(id);
    }

    // PUT /api/violations/{id}/process : 处理违章
    @PutMapping("/violations/{id}/process")
    public Map<String, Object> processViolation(@PathVariable String id, @RequestBody Map<String, Object> processInfo) {
        violationService.processViolation(id, processInfo);
        return Map.of("message", "Violation processed successfully");
    }

    // POST /api/violations/increment : 增加违章计数
    @PostMapping("/violations/increment")
    public Map<String, Object> incrementViolationCount() {
        long newCount = violationService.incrementViolationCount();
        return Map.of(
                "count", newCount,
                "message", "Violation count incremented successfully");
    }

    // 获取违章总数（从 Redis 实时读取）
    @GetMapping("/violations/count")
    public Map<String, Object> getViolationCount() {
        return Map.of("count", violationService.getViolationCount());
    }
}