package com.traffic.management.config;

import com.traffic.management.handler.TrafficDataWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 独立的 WebSocket 配置，避免修改原有的 WebSocketConfig
 */
@Configuration
@EnableWebSocket
public class TrafficWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private TrafficDataWebSocketHandler trafficDataWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册 /ws/traffic 端点，允许跨域
        registry.addHandler(trafficDataWebSocketHandler, "/ws/traffic")
                .setAllowedOrigins("*");
    }
}