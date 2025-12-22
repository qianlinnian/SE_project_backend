package com.traffic.management.controller;

import com.traffic.management.entity.IntersectionDirection;
import com.traffic.management.entity.Violation;
import com.traffic.management.service.MultiDirectionTrafficLightService;
import com.traffic.management.repository.IntersectionDirectionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 多方向交通信号灯控制器
 * 支持每个路口四个方向的独立信号控制
 */
@RestController
@RequestMapping("/api/multi-direction-traffic")
public class MultiDirectionTrafficController {

        @Autowired
        private MultiDirectionTrafficLightService multiDirectionTrafficLightService;

        @Autowired
        private IntersectionDirectionRepository intersectionDirectionRepository;

        /**
         * 获取路口所有方向的配置信息
         */
        @GetMapping("/intersections/{intersectionId}/directions")
        public List<IntersectionDirection> getIntersectionDirections(@PathVariable Long intersectionId) {
                return intersectionDirectionRepository.findByIntersectionId(intersectionId);
        }

        /**
         * 获取路口所有方向的当前信号灯状态
         */
        @GetMapping("/intersections/{intersectionId}/status")
        public Map<String, MultiDirectionTrafficLightService.DirectionLightState> getIntersectionStatus(
                        @PathVariable Long intersectionId) {
                return multiDirectionTrafficLightService.getIntersectionAllDirectionsStatus(intersectionId);
        }

        /**
         * 获取指定路口指定方向指定转弯类型的当前信号灯状态
         */
        @GetMapping("/intersections/{intersectionId}/directions/{direction}/turns/{turnType}/status")
        public Map<String, Object> getSpecificDirectionStatus(
                        @PathVariable Long intersectionId,
                        @PathVariable String direction,
                        @PathVariable String turnType) {

                try {
                        Violation.Direction dir = Violation.Direction.valueOf(direction.toUpperCase());
                        Violation.TurnType turn = Violation.TurnType.valueOf(turnType.toUpperCase());

                        IntersectionDirection.LightPhase currentState = multiDirectionTrafficLightService
                                        .getCurrentLightState(intersectionId, dir, turn, LocalDateTime.now());

                        return Map.of(
                                        "intersectionId", intersectionId,
                                        "direction", dir.name(),
                                        "directionName", dir.getChineseName(),
                                        "turnType", turn.name(),
                                        "turnTypeName", turn.getChineseName(),
                                        "currentPhase", currentState.name(),
                                        "timestamp", LocalDateTime.now().toString());
                } catch (IllegalArgumentException e) {
                        return Map.of(
                                        "success", false,
                                        "message", "无效的方向或转弯类型: " + e.getMessage());
                }
        }

        /**
         * 模拟设置指定方向指定转弯类型的信号灯状态
         */
        @PostMapping("/intersections/{intersectionId}/simulate")
        public Map<String, Object> simulateDirectionLight(
                        @PathVariable Long intersectionId,
                        @RequestBody Map<String, Object> requestData) {

                try {
                        String direction = requestData.getOrDefault("direction", "SOUTH").toString();
                        String turnType = requestData.getOrDefault("turnType", "STRAIGHT").toString();
                        String lightPhase = requestData.getOrDefault("lightPhase", "RED").toString();
                        int durationSeconds = Integer.parseInt(requestData.getOrDefault("duration", "60").toString());

                        Violation.Direction dir = Violation.Direction.valueOf(direction.toUpperCase());
                        Violation.TurnType turn = Violation.TurnType.valueOf(turnType.toUpperCase());
                        IntersectionDirection.LightPhase phase = IntersectionDirection.LightPhase
                                        .valueOf(lightPhase.toUpperCase());

                        multiDirectionTrafficLightService.simulateDirectionLightState(intersectionId, dir, turn, phase,
                                        durationSeconds);

                        return Map.of(
                                        "success", true,
                                        "message", String.format("路口%d %s方向%s信号灯已设置为%s状态，持续%d秒",
                                                        intersectionId, dir.getChineseName(), turn.getChineseName(),
                                                        phase.name(), durationSeconds));
                } catch (IllegalArgumentException e) {
                        return Map.of(
                                        "success", false,
                                        "message", "参数错误: " + e.getMessage());
                }
        }

        /**
         * 验证违章行为是否构成违法（用于测试）
         */
        @PostMapping("/intersections/{intersectionId}/validate-violation")
        public Map<String, Object> validateViolation(
                        @PathVariable Long intersectionId,
                        @RequestBody Map<String, Object> violationData) {

                try {
                        String directionStr = violationData.getOrDefault("direction", "SOUTH").toString();
                        String turnTypeStr = violationData.getOrDefault("turnType", "STRAIGHT").toString();
                        String violationTypeStr = violationData.getOrDefault("violationType", "RED_LIGHT").toString();

                        Violation.Direction direction = Violation.Direction.valueOf(directionStr.toUpperCase());
                        Violation.TurnType turnType = Violation.TurnType.valueOf(turnTypeStr.toUpperCase());
                        Violation.ViolationType violationType = Violation.ViolationType
                                        .valueOf(violationTypeStr.toUpperCase());

                        boolean isViolation = multiDirectionTrafficLightService
                                        .validateViolationWithMultiDirectionLight(
                                                        intersectionId, direction, turnType, violationType,
                                                        LocalDateTime.now());

                        return Map.of(
                                        "intersectionId", intersectionId,
                                        "direction", direction.getChineseName(),
                                        "turnType", turnType.getChineseName(),
                                        "violationType", violationType.name(),
                                        "isViolation", isViolation,
                                        "message", isViolation ? "构成违章" : "不构成违章");
                } catch (Exception e) {
                        return Map.of(
                                        "success", false,
                                        "message", "验证失败: " + e.getMessage());
                }
        }

        /**
         * 清除路口缓存
         */
        @DeleteMapping("/intersections/{intersectionId}/cache")
        public Map<String, Object> clearCache(@PathVariable Long intersectionId) {
                multiDirectionTrafficLightService.clearIntersectionCache(intersectionId);
                return Map.of(
                                "success", true,
                                "message", "路口" + intersectionId + "的缓存已清除");
        }

        /**
         * 获取指定方向的配置详情
         */
        @GetMapping("/intersections/{intersectionId}/directions/{direction}/config")
        public Map<String, Object> getDirectionConfig(
                        @PathVariable Long intersectionId,
                        @PathVariable String direction) {

                try {
                        IntersectionDirection.Direction dir = IntersectionDirection.Direction
                                        .valueOf(direction.toUpperCase());

                        // 获取该方向的配置信息
                        List<IntersectionDirection> directionConfigs = intersectionDirectionRepository
                                        .findByIntersectionId(intersectionId);

                        // 过滤出指定方向的配置
                        List<IntersectionDirection> filteredConfigs = directionConfigs.stream()
                                        .filter(config -> config.getDirection() == dir)
                                        .toList();

                        return Map.of(
                                        "intersectionId", intersectionId,
                                        "direction", direction.toUpperCase(),
                                        "directionName", dir.getChineseName(),
                                        "configurations", filteredConfigs,
                                        "supportedTurnTypes", Map.of(
                                                        "STRAIGHT", "直行",
                                                        "LEFT_TURN", "左转",
                                                        "RIGHT_TURN", "右转"),
                                        "timestamp", LocalDateTime.now());
                } catch (IllegalArgumentException e) {
                        return Map.of(
                                        "success", false,
                                        "message", "无效的方向: " + direction);
                }
        }

        /**
         * 获取支持的方向和转弯类型
         */
        @GetMapping("/metadata")
        public Map<String, Object> getMetadata() {
                return Map.of(
                                "directions", Map.of(
                                                "EAST", "东",
                                                "SOUTH", "南",
                                                "WEST", "西",
                                                "NORTH", "北"),
                                "turnTypes", Map.of(
                                                "STRAIGHT", "直行",
                                                "LEFT_TURN", "左转",
                                                "RIGHT_TURN", "右转",
                                                "U_TURN", "掉头"),
                                "lightPhases", Map.of(
                                                "RED", "红灯",
                                                "YELLOW", "黄灯",
                                                "GREEN", "绿灯"),
                                "violationTypes", Map.of(
                                                "RED_LIGHT", "闯红灯",
                                                "ILLEGAL_TURN", "违法转弯",
                                                "WRONG_WAY", "逆行",
                                                "CROSS_SOLID_LINE", "跨实线"));
        }
}