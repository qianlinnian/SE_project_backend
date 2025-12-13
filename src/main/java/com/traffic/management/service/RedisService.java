package com.traffic.management.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 存储实时路况数据
    public void setTrafficStatus(String intersectionId, Map<String, Object> status) {
        String key = "intersection:" + intersectionId;
        redisTemplate.opsForHash().putAll(key, status);
        // 设置过期时间，比如5分钟
        redisTemplate.expire(key, Duration.ofMinutes(5));
    }

    // 获取实时路况数据
    public Map<Object, Object> getTrafficStatus(String intersectionId) {
        String key = "intersection:" + intersectionId;
        return redisTemplate.opsForHash().entries(key);
    }

    // 获取所有路口列表
    public Set<String> getAllIntersections() {
        return redisTemplate.keys("intersection:*");
    }

    // 存储宏观统计数据
    public void setDashboardStats(Map<String, Object> stats) {
        String key = "dashboard:stats";
        redisTemplate.opsForHash().putAll(key, stats);
        redisTemplate.expire(key, Duration.ofHours(1));
    }

    // 获取宏观统计数据
    public Map<Object, Object> getDashboardStats() {
        String key = "dashboard:stats";
        return redisTemplate.opsForHash().entries(key);
    }

    // 存储热力图数据
    public void setHeatmapData(List<Map<String, Object>> heatmap) {
        String key = "dashboard:heatmap";
        redisTemplate.opsForValue().set(key, heatmap);
        redisTemplate.expire(key, Duration.ofMinutes(10));
    }

    // 获取热力图数据
    public List<Map<String, Object>> getHeatmapData() {
        String key = "dashboard:heatmap";
        Object data = redisTemplate.opsForValue().get(key);
        return data != null ? (List<Map<String, Object>>) data : new ArrayList<>();
    }

    // 存储Token
    public void setToken(String userId, String token, long expirationTime) {
        String key = "token:" + userId;
        redisTemplate.opsForValue().set(key, token, expirationTime, TimeUnit.MILLISECONDS);
    }

    // 获取Token
    public String getToken(String userId) {
        String key = "token:" + userId;
        return (String) redisTemplate.opsForValue().get(key);
    }

    // 删除Token
    public void deleteToken(String userId) {
        String key = "token:" + userId;
        redisTemplate.delete(key);
    }

    // 计数器：增加违章总数
    public Long incrementViolationCount() {
        String key = "violation:total:today";
        return redisTemplate.opsForValue().increment(key);
    }

    // 获取违章总数
    public Long getViolationCount() {
        String key = "violation:total:today";
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : 0L;
    }

    // 重置计数器（每天重置）
    public void resetViolationCount() {
        String key = "violation:total:today";
        redisTemplate.delete(key);
    }

    // 上报违章记录
    public void reportViolation(Map<String, Object> violation) {
        String key = "violations:list";
        String violationId = UUID.randomUUID().toString();
        violation.put("id", violationId);
        violation.put("status", "UNPROCESSED");
        violation.put("timestamp", System.currentTimeMillis());

        redisTemplate.opsForList().leftPush(key, violation);
        // 存储详细记录
        String detailKey = "violation:" + violationId;
        redisTemplate.opsForHash().putAll(detailKey, violation);
        redisTemplate.expire(detailKey, Duration.ofDays(30)); // 保留30天
    }

    // 获取违章记录列表
    public List<Map<String, Object>> getViolations(int page, int size) {
        String key = "violations:list";
        long start = (page - 1) * size;
        long end = start + size - 1;
        List<Object> violations = redisTemplate.opsForList().range(key, start, end);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object v : violations) {
            if (v instanceof Map) {
                result.add((Map<String, Object>) v);
            }
        }
        return result;
    }

    // 获取单条违章详情
    public Map<Object, Object> getViolationDetail(String violationId) {
        String key = "violation:" + violationId;
        return redisTemplate.opsForHash().entries(key);
    }

    // 处理违章
    public void processViolation(String violationId, Map<String, Object> processInfo) {
        String key = "violation:" + violationId;
        redisTemplate.opsForHash().putAll(key, processInfo);
        redisTemplate.opsForHash().put(key, "status", "PROCESSED");
    }
}