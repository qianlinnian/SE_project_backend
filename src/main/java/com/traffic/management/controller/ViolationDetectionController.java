package com.traffic.management.controller;

import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;

/**
 * 简化的违章检测控制器 - 用于测试
 */
@RestController
@RequestMapping("/api/violation-detection")
public class ViolationDetectionController {

        @PostMapping("/detect-frame")
        public Map<String, Object> detectFrame(@RequestBody Map<String, Object> detectionData) {
                return Map.of(
                                "success", true,
                                "violationId", UUID.randomUUID().toString(),
                                "message", "违章检测处理完成",
                                "data", detectionData,
                                "timestamp", LocalDateTime.now());
        }

        @GetMapping("/tasks/statistics")
        public Map<String, Object> getTasksStatistics() {
                return Map.of(
                                "totalTasks", 125,
                                "completedTasks", 118,
                                "processingTasks", 5,
                                "failedTasks", 2,
                                "totalViolations", 456,
                                "violationsByType", Map.of(
                                                "闯红灯", 156,
                                                "逆行", 98,
                                                "压实线", 112,
                                                "违法转弯", 90));
        }

        @GetMapping("/intersection/{intersectionId}/tasks")
        public Map<String, Object> getIntersectionTasks(@PathVariable Long intersectionId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "10") int size) {
                return Map.of(
                                "content", java.util.List.of(
                                                Map.of(
                                                                "taskId", "550e8400-e29b-41d4-a716-446655440000",
                                                                "status", "COMPLETED",
                                                                "direction", "SOUTH",
                                                                "violationCount", 5,
                                                                "createdTime", LocalDateTime.now().minusHours(2)),
                                                Map.of(
                                                                "taskId", "550e8400-e29b-41d4-a716-446655440001",
                                                                "status", "PROCESSING",
                                                                "direction", "EAST",
                                                                "violationCount", 0,
                                                                "createdTime", LocalDateTime.now().minusMinutes(30))),
                                "totalElements", 25,
                                "totalPages", 3,
                                "intersectionId", intersectionId);
        }

        @GetMapping("/task/{taskId}/status")
        public Map<String, Object> getTaskStatus(@PathVariable String taskId) {
                return Map.of(
                                "taskId", taskId,
                                "status", "COMPLETED",
                                "progress", 100,
                                "createdTime", LocalDateTime.now().minusHours(1),
                                "completedTime", LocalDateTime.now().minusMinutes(30),
                                "violationCount", 3,
                                "intersectionId", 1,
                                "direction", "SOUTH");
        }

        @PostMapping("/ai-callback")
        public Map<String, Object> receiveAICallback(@RequestBody Map<String, Object> callbackData) {
                return Map.of(
                                "success", true,
                                "message", "AI回调处理成功",
                                "taskId", callbackData.get("taskId"),
                                "timestamp", LocalDateTime.now());
        }

        @PostMapping("/ai-completed")
        public Map<String, Object> aiAnalysisCompleted(@RequestBody Map<String, Object> completionData) {
                return Map.of(
                                "success", true,
                                "message", "AI分析完成确认",
                                "taskId", completionData.get("taskId"),
                                "timestamp", LocalDateTime.now());
        }

        /**
         * 方向特定的违章检测接口
         */
        @PostMapping("/directions/{direction}/detect-frame")
        public Map<String, Object> detectFrameByDirection(
                        @PathVariable String direction,
                        @RequestBody Map<String, Object> detectionData) {

                // 将方向信息添加到检测数据中
                detectionData.put("direction", direction.toUpperCase());

                // 如果没有指定转向类型，根据违章类型推断
                if (!detectionData.containsKey("turnType")) {
                        String violationType = detectionData.getOrDefault("violationType", "").toString();
                        if (violationType.contains("转弯")) {
                                detectionData.put("turnType", "LEFT_TURN");
                        } else {
                                detectionData.put("turnType", "STRAIGHT");
                        }
                }

                return Map.of(
                                "success", true,
                                "violationId", UUID.randomUUID().toString(),
                                "message", "方向特定违章检测处理完成",
                                "direction", direction.toUpperCase(),
                                "data", detectionData,
                                "timestamp", LocalDateTime.now());
        }
}