package com.traffic.management.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 统计数据同步服务
 *
 * 定时从数据库统计数据并同步到Redis,
 * 用于实时监控大屏显示
 */
@Slf4j
@Service
public class StatisticsSyncService {

    @Autowired
    private ViolationService violationService;

    @Autowired
    private RedisService redisService;

    /**
     * 每5分钟自动同步一次统计数据到Redis
     * 用于实时监控大屏
     */
    @Scheduled(fixedRate = 300000) // 5分钟 = 300000毫秒
    public void syncStatisticsToRedis() {
        try {
            log.info("[统计同步] 开始同步统计数据到Redis...");

            // 1. 从数据库查询最近24小时的统计数据
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(24);

            Map<String, Object> overview = violationService.getStatisticsOverview(startTime, endTime);

            // 2. 构建Redis统计数据
            Map<String, Object> dashboardStats = new HashMap<>();
            dashboardStats.put("totalViolations", overview.get("total"));
            dashboardStats.put("pendingViolations", overview.get("pending"));
            dashboardStats.put("confirmedViolations", overview.get("confirmed"));
            dashboardStats.put("rejectedViolations", overview.get("rejected"));
            dashboardStats.put("growthRate", overview.get("growthRate"));
            dashboardStats.put("lastUpdated", LocalDateTime.now().toString());

            // 3. 写入Redis
            redisService.setDashboardStats(dashboardStats);

            log.info("[统计同步] ✅ 统计数据同步成功: total={}, pending={}, confirmed={}",
                    overview.get("total"), overview.get("pending"), overview.get("confirmed"));

        } catch (Exception e) {
            log.error("[统计同步] ❌ 统计数据同步失败", e);
        }
    }

    /**
     * 每10分钟同步一次热力图数据到Redis
     */
    @Scheduled(fixedRate = 600000) // 10分钟
    public void syncHeatmapToRedis() {
        try {
            log.info("[热力图同步] 开始同步热力图数据到Redis...");

            // 查询最近7天的热力图数据
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusDays(7);

            Map<String, Object> heatmapResult = violationService.getStatisticsHeatmap(startTime, endTime);
            List<List<Object>> heatmapData = (List<List<Object>>) heatmapResult.get("data");

            // 转换为前端需要的格式
            List<Map<String, Object>> formattedData = new ArrayList<>();
            for (List<Object> item : heatmapData) {
                Map<String, Object> point = new HashMap<>();
                point.put("x", item.get(0)); // hour
                point.put("y", item.get(1)); // dayOfWeek
                point.put("value", item.get(2)); // count
                formattedData.add(point);
            }

            // 写入Redis
            redisService.setHeatmapData(formattedData);

            log.info("[热力图同步] ✅ 热力图数据同步成功: {} 个数据点", formattedData.size());

        } catch (Exception e) {
            log.error("[热力图同步] ❌ 热力图数据同步失败", e);
        }
    }

    /**
     * 手动触发同步 (用于测试或手动刷新)
     */
    public Map<String, Object> manualSync() {
        log.info("[手动同步] 触发手动同步...");

        syncStatisticsToRedis();
        syncHeatmapToRedis();

        return Map.of(
            "success", true,
            "message", "手动同步完成",
            "syncTime", LocalDateTime.now().toString()
        );
    }
}
