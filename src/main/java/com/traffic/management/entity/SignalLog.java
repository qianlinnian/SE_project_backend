package com.traffic.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 信号灯操作日志实体
 */
@Entity
@Table(name = "signal_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "intersection_id", nullable = false)
    private Long intersectionId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_config", columnDefinition = "json")
    private Map<String, Object> oldConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_config", nullable = false, columnDefinition = "json")
    private Map<String, Object> newConfig;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(length = 255)
    private String reason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * 操作类型常量
     */
    public static class ActionType {
        public static final String AUTO_ADJUST = "AUTO_ADJUST";
        public static final String MANUAL_OVERRIDE = "MANUAL_OVERRIDE";
        public static final String MODE_CHANGE = "MODE_CHANGE";
    }
}
