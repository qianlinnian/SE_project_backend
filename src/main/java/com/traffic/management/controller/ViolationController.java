package com.traffic.management.controller;

import com.traffic.management.handler.AlertWebSocketHandler;
import com.traffic.management.service.ViolationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    public Map<String, Object> getViolations(
            @RequestParam(defaultValue = "0") int page,  // 改为0-based，与前端保持一致
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type) {
        List<Map<String, Object>> violations = violationService.getViolations(page, size, search, type);
        long total = violationService.getViolationCountWithFilter(search, type);

        return Map.of(
                "violations", violations,
                "total", total,
                "page", page + 1,  // 返回给前端时转换为1-based显示
                "size", size
        );
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

    @GetMapping("/violations/count")
    public Map<String, Object> getViolationCount() {
        return Map.of("count", violationService.getViolationCount());
    }

    // ========== 统计分析 API ==========

    /**
     * 按违规类型统计
     * GET /api/violations/statistics/by-type
     */
    @GetMapping("/violations/statistics/by-type")
    public Map<String, Object> getStatisticsByType(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        LocalDateTime startTime = getStartDateTime(startDate);
        LocalDateTime endTime = getEndDateTime(endDate);

        return violationService.getStatisticsByType(startTime, endTime);
    }

    // ========== 辅助方法 ==========

    /**
     * 获取开始时间（默认30天前）
     */
    private LocalDateTime getStartDateTime(LocalDate startDate) {
        if (startDate == null) {
            return LocalDateTime.now().minusDays(30).with(LocalTime.MIN);
        }
        return startDate.atStartOfDay();
    }

    /**
     * 获取结束时间（默认今天）
     */
    private LocalDateTime getEndDateTime(LocalDate endDate) {
        if (endDate == null) {
            return LocalDateTime.now().with(LocalTime.MAX);
        }
        return endDate.atTime(LocalTime.MAX);
    }
}