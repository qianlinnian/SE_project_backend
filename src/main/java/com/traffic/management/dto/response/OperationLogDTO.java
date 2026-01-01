package com.traffic.management.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 操作日志响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogDTO {

    /**
     * 日志ID
     */
    private Long id;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人用户名
     */
    private String operatorUsername;

    /**
     * 操作人姓名
     */
    private String operatorFullName;

    /**
     * 操作类型
     */
    private String operationType;

    /**
     * 操作目标类型
     */
    private String targetType;

    /**
     * 操作目标ID
     */
    private Long targetId;

    /**
     * 操作详情
     */
    private Map<String, Object> operationDetails;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 操作时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
