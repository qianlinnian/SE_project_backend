package com.traffic.management.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 处理 /ws/traffic 连接，用于向前端推送实时交通数据
 */
@Slf4j
@Component
public class TrafficDataWebSocketHandler extends TextWebSocketHandler {

    // 存储所有活跃的 WebSocket 会话
    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("New Traffic WebSocket connection: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("Traffic WebSocket connection closed: {}", session.getId());
    }

    /**
     * 广播消息给所有连接的客户端
     */
    public void broadcast(String message) {
        log.info("🔔 准备广播交通数据，当前连接数: {}", sessions.size());

        if (sessions.isEmpty()) {
            log.warn("⚠️ 没有前端连接到WebSocket! 数据无法推送");
            return;
        }

        sessions.forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                    log.debug("✅ 成功发送到session: {}", session.getId());
                } catch (IOException e) {
                    log.error("❌ 发送失败 session {}", session.getId(), e);
                }
            }
        });

        log.info("📡 广播完成，发送给 {} 个客户端", sessions.size());
    }
}