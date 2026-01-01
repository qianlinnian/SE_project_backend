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
 * 操作日志实体
 */
@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 操作人ID，外键关联users表（NULL表示系统操作）
     */
    @Column(name = "operator_id")
    private Long operatorId;

    /**
     * 操作类型（如：CREATE_USER, SUSPEND_USER, CONFIRM_VIOLATION, APPROVE_APPEAL）
     */
    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    /**
     * 操作目标类型（如：USER, VIOLATION, APPEAL）
     */
    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    /**
     * 操作目标ID
     */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    /**
     * 操作详情（JSON格式，存储操作前后的数据对比）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operation_details", columnDefinition = "json")
    private Map<String, Object> operationDetails;

    /**
     * 操作IP地址
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * 用户代理信息
     */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /**
     * 操作时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * 操作类型常量
     */
    public static class OperationType {
        // 用户操作
        public static final String CREATE_USER = "CREATE_USER";
        public static final String DELETE_USER = "DELETE_USER";
        public static final String SUSPEND_USER = "SUSPEND_USER";
        public static final String ACTIVATE_USER = "ACTIVATE_USER";
        public static final String LOGIN = "LOGIN";
        public static final String LOGOUT = "LOGOUT";

        // 违规操作
        public static final String CONFIRM_VIOLATION = "CONFIRM_VIOLATION";
        public static final String REJECT_VIOLATION = "REJECT_VIOLATION";

        // 申诉操作
        public static final String APPROVE_APPEAL = "APPROVE_APPEAL";
        public static final String REJECT_APPEAL = "REJECT_APPEAL";
    }

    /**
     * 目标类型常量
     */
    public static class TargetType {
        public static final String USER = "USER";
        public static final String VIOLATION = "VIOLATION";
        public static final String APPEAL = "APPEAL";
    }
}
