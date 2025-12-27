package com.traffic.management.controller;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 简单测试控制器
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "OK",
                "message", "服务运行正常",
                "timestamp", System.currentTimeMillis());
    }

    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> request) {
        return Map.of(
                "success", true,
                "echo", request,
                "message", "回声测试成功");
    }
}