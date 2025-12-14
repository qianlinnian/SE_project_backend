package com.traffic.management.controller;

import com.traffic.management.service.RedisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/redis-test")
public class RedisTestController {

    @Autowired
    private RedisService redisService;

    @PostMapping("/traffic/{intersectionId}")
    public String setTrafficStatus(@PathVariable String intersectionId) {
        Map<String, Object> status = new HashMap<>();
        status.put("flow", 120);
        status.put("status", "CONGESTED");
        redisService.setTrafficStatus(intersectionId, status);
        return "Traffic status set for " + intersectionId;
    }

    @GetMapping("/traffic/{intersectionId}")
    public Map<Object, Object> getTrafficStatus(@PathVariable String intersectionId) {
        return redisService.getTrafficStatus(intersectionId);
    }

    @PostMapping("/violation")
    public String incrementViolation() {
        Long count = redisService.incrementViolationCount();
        return "Violation count: " + count;
    }

    @GetMapping("/violations")
    public Long getViolationCount() {
        return redisService.getViolationCount();
    }
}