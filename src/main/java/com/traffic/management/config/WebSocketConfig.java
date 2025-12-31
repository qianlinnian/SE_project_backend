package com.traffic.management.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import com.traffic.management.handler.AlertWebSocketHandler;
import com.traffic.management.handler.TrafficDataWebSocketHandler;

/**
 * WebSocket配置
 * 支持原有的告警WebSocket和新的任务通知STOMP协议
 */
@Configuration
@EnableWebSocket
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketConfigurer, WebSocketMessageBrokerConfigurer {

    private final AlertWebSocketHandler alertWebSocketHandler;
    private final TrafficDataWebSocketHandler trafficDataWebSocketHandler;

    public WebSocketConfig(AlertWebSocketHandler alertWebSocketHandler,
                          TrafficDataWebSocketHandler trafficDataWebSocketHandler) {
        this.alertWebSocketHandler = alertWebSocketHandler;
        this.trafficDataWebSocketHandler = trafficDataWebSocketHandler;
    }

    // 原有的WebSocket配置（用于告警）
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertWebSocketHandler, "/ws/alerts")
                .setAllowedOrigins("*"); // 在生产环境中应该设置具体的域名

        // 注册交通数据WebSocket端点
        registry.addHandler(trafficDataWebSocketHandler, "/ws/traffic")
                .setAllowedOrigins("*");
    }

    // 新的STOMP协议配置（用于任务通知）
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，用于发送消息给客户端
        config.enableSimpleBroker("/topic");
        // 设置应用程序消息前缀
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册WebSocket端点，客户端连接的地址
        registry.addEndpoint("/ws-notifications")
                .setAllowedOriginPatterns("*") // 允许跨域
                .withSockJS(); // 支持SockJS回退
    }
}