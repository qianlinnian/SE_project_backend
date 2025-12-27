package com.traffic.management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.management.dto.traffic.IntersectionDTO;
import com.traffic.management.dto.traffic.TrafficDataDTO;
import com.traffic.management.entity.TrafficFlowRecord;
import com.traffic.management.handler.TrafficDataWebSocketHandler;
import com.traffic.management.repository.TrafficFlowRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 负责处理交通流数据的接收、存储、广播和查询
 */
@Slf4j
@Service
public class TrafficDataService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TrafficFlowRecordRepository trafficFlowRepository;

    @Autowired
    private TrafficDataWebSocketHandler webSocketHandler;

    @Autowired
    private ObjectMapper objectMapper;

    // Redis Key 常量
    private static final String REDIS_KEY_LATEST_TRAFFIC = "traffic:ai:latest";
    private static final String REDIS_KEY_LAST_UPDATE_TIME = "traffic:ai:last_update_ts";
    private static final long REDIS_EXPIRE_HOURS = 24;

    /**
     * 处理从 LLM 接收到的数据
     */
    @Async
    public void processIngressData(TrafficDataDTO dataDTO) {
        try {
            // 1. 广播到 WebSocket
            Map<String, Object> wsMessage = new HashMap<>();
            wsMessage.put("type", "traffic_update");
            wsMessage.put("data", dataDTO);
            String jsonMessage = objectMapper.writeValueAsString(wsMessage);
            webSocketHandler.broadcast(jsonMessage);

            // 2. 更新 Redis (数据本身 + 更新时间戳)
            String rawJson = objectMapper.writeValueAsString(dataDTO);
            redisTemplate.opsForValue().set(REDIS_KEY_LATEST_TRAFFIC, rawJson, REDIS_EXPIRE_HOURS, TimeUnit.HOURS);
            redisTemplate.opsForValue().set(REDIS_KEY_LAST_UPDATE_TIME, LocalDateTime.now().toString(), REDIS_EXPIRE_HOURS, TimeUnit.HOURS);

            // 3. 全量异步存入 MySQL
            saveHistoryRecord(dataDTO);

        } catch (Exception e) {
            log.error("Error processing traffic ingress data", e);
        }
    }

    private void saveHistoryRecord(TrafficDataDTO dto) {
        try {
            int totalQueue = dto.getTotalQueue() != null ? dto.getTotalQueue() :
                    (dto.getIntersections() != null ? dto.getIntersections().stream().mapToInt(IntersectionDTO::getQueueLength).sum() : 0);
            
            int totalVehicles = dto.getTotalVehicles() != null ? dto.getTotalVehicles() :
                    (dto.getIntersections() != null ? dto.getIntersections().stream().mapToInt(IntersectionDTO::getVehicleCount).sum() : 0);

            Map<String, Object> snapshot = objectMapper.convertValue(dto, Map.class);

            TrafficFlowRecord record = TrafficFlowRecord.builder()
                    .simulationTimestamp(dto.getTimestamp())
                    .step(dto.getStep())
                    .controlMode(dto.getControlMode())
                    .totalQueue(totalQueue)
                    .totalVehicles(totalVehicles)
                    .fullDataSnapshot(snapshot)
                    .build();

            trafficFlowRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to save traffic history", e);
        }
    }

    public TrafficDataDTO getLatestTrafficData() {
        String json = redisTemplate.opsForValue().get(REDIS_KEY_LATEST_TRAFFIC);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, TrafficDataDTO.class);
        } catch (Exception e) {
            log.error("Error parsing traffic data from Redis", e);
            return null;
        }
    }

    public List<TrafficFlowRecord> getHistory(LocalDateTime start, LocalDateTime end, int limit) {
        if (start != null && end != null) {
            return trafficFlowRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(start, end);
        } else {
            return trafficFlowRepository.findTop100ByOrderByCreatedAtDesc();
        }
    }

    /**
     * 获取最后一次接收数据的时间（用于系统状态检查）
     */
    public LocalDateTime getLastDataReceivedTime() {
        String timeStr = redisTemplate.opsForValue().get(REDIS_KEY_LAST_UPDATE_TIME);
        if (timeStr == null) return null;
        return LocalDateTime.parse(timeStr);
    }
}