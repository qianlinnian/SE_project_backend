package com.traffic.management.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
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
}