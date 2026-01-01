package com.traffic.management.controller;

import com.traffic.management.dto.traffic.IntersectionDTO;
import com.traffic.management.dto.traffic.TrafficDataDTO;
import com.traffic.management.service.RedisService;
import com.traffic.management.service.TrafficDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class TrafficMonitorController {

    @Autowired
    private RedisService redisService;

    @Autowired
    private TrafficDataService trafficDataService;

    // GET /api/intersections : 获取所有路口列表及基础信息
    @GetMapping("/intersections")
    public List<Map<String, Object>> getAllIntersections() {
        Set<String> keys = redisService.getAllIntersections();
        List<Map<String, Object>> intersections = new ArrayList<>();

        for (String key : keys) {
            String intersectionId = key.replace("intersection:", "");
            Map<Object, Object> data = redisService.getTrafficStatus(intersectionId);
            if (!data.isEmpty()) {
                Map<String, Object> intersection = new HashMap<>();
                intersection.put("id", intersectionId);
                intersection.put("data", data);
                intersections.add(intersection);
            }
        }

        return intersections;
    }

    // GET /api/intersections/{id}/realtime : 获取某路口的实时数据
    @GetMapping("/intersections/{id}/realtime")
    public Map<Object, Object> getIntersectionRealtime(@PathVariable String id) {
        return redisService.getTrafficStatus(id);
    }

    // GET /api/dashboard/stats : 获取宏观交通趋势分析报告
    @GetMapping("/dashboard/stats")
    public Map<Object, Object> getDashboardStats() {
        return redisService.getDashboardStats();
    }

    // GET /api/dashboard/heatmap : 车辆分布热力图数据（基于LLM实时交通数据）
    @GetMapping("/dashboard/heatmap")
    public List<Map<String, Object>> getHeatmapData() {
        List<Map<String, Object>> result = new ArrayList<>();

        // 从 TrafficDataService 获取LLM最新交通数据
        TrafficDataDTO trafficData = trafficDataService.getLatestTrafficData();
        if (trafficData == null || trafficData.getIntersections() == null) {
            return result;
        }

        // 转换每个路口的数据为热力图格式
        for (IntersectionDTO intersection : trafficData.getIntersections()) {
            Map<String, Object> item = new HashMap<>();
            item.put("intersectionId", intersection.getId());
            item.put("queueLength", intersection.getQueueLength());
            item.put("vehicleCount", intersection.getVehicleCount());
            item.put("signalPhase", intersection.getSignalPhase());

            // 计算拥堵级别
            int queueLength = intersection.getQueueLength() != null ? intersection.getQueueLength() : 0;
            String congestionLevel = calculateCongestionLevel(queueLength);
            item.put("congestionLevel", congestionLevel);

            result.add(item);
        }

        return result;
    }

    /**
     * 计算拥堵级别
     * queue_length > 10: 严重拥堵 (severe)
     * queue_length > 5: 轻度拥堵 (mild)
     * else: 畅通 (clear)
     */
    private String calculateCongestionLevel(int queueLength) {
        if (queueLength > 10) {
            return "severe";
        } else if (queueLength > 5) {
            return "mild";
        } else {
            return "clear";
        }
    }

    // 模拟设置路口数据（用于测试）
    @PostMapping("/intersections/{id}")
    public String setIntersectionData(@PathVariable String id, @RequestBody Map<String, Object> data) {
        redisService.setTrafficStatus(id, data);
        return "Intersection data set for " + id;
    }

    // 模拟设置统计数据（用于测试）
    @PostMapping("/dashboard/stats")
    public String setDashboardStats(@RequestBody Map<String, Object> stats) {
        redisService.setDashboardStats(stats);
        return "Dashboard stats set";
    }

    // 模拟设置热力图数据（用于测试）
    @PostMapping("/dashboard/heatmap")
    public String setHeatmapData(@RequestBody List<Map<String, Object>> heatmap) {
        redisService.setHeatmapData(heatmap);
        return "Heatmap data set";
    }
}