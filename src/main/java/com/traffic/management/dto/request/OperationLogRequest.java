package com.traffic.management.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 操作日志记录请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogRequest {

    /**
     * 操作类型
     */
    @NotBlank(message = "操作类型不能为空")
    private String operationType;

    /**
     * 操作目标类型
     */
    @NotBlank(message = "目标类型不能为空")
    private String targetType;

    /**
     * 操作目标ID
     */
    @NotNull(message = "目标ID不能为空")
    private Long targetId;

    /**
     * 操作详情（可选）
     */
    private Map<String, Object> operationDetails;
}
