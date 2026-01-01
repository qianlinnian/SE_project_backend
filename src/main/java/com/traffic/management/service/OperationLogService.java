package com.traffic.management.service;

import com.traffic.management.dto.request.OperationLogRequest;
import com.traffic.management.dto.response.OperationLogDTO;
import com.traffic.management.dto.response.PageResponse;
import com.traffic.management.entity.OperationLog;
import com.traffic.management.repository.OperationLogRepository;
import com.traffic.management.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 操作日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;
    private final UserRepository userRepository;

    /**
     * 记录操作日志
     */
    @Transactional
    public void log(Long operatorId, OperationLogRequest request, HttpServletRequest httpRequest) {
        OperationLog operationLog = OperationLog.builder()
                .operatorId(operatorId)
                .operationType(request.getOperationType())
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .operationDetails(request.getOperationDetails())
                .ipAddress(getClientIp(httpRequest))
                .userAgent(httpRequest.getHeader("User-Agent"))
                .build();

        operationLogRepository.save(operationLog);
        log.info("记录操作日志: 操作人={}, 类型={}, 目标={}:{}",
                operatorId, request.getOperationType(), request.getTargetType(), request.getTargetId());
    }

    /**
     * 记录系统操作日志（无操作人）
     */
    @Transactional
    public void logSystem(String operationType, String targetType, Long targetId, Map<String, Object> details) {
        OperationLog operationLog = OperationLog.builder()
                .operatorId(null)  // 系统操作
                .operationType(operationType)
                .targetType(targetType)
                .targetId(targetId)
                .operationDetails(details)
                .build();

        operationLogRepository.save(operationLog);
        log.info("记录系统操作日志: 类型={}, 目标={}:{}", operationType, targetType, targetId);
    }

    /**
     * 分页查询操作日志
     */
    public PageResponse<OperationLogDTO> queryLogs(
            Integer page,
            Integer pageSize,
            Long operatorId,
            String operationType,
            String targetType,
            LocalDateTime startTime,
            LocalDateTime endTime) {

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("createdAt").descending());
        Page<OperationLog> logPage;

        // 根据条件查询
        if (operatorId != null && operationType != null && startTime != null && endTime != null) {
            logPage = operationLogRepository.findByOperatorIdAndOperationTypeAndCreatedAtBetween(
                    operatorId, operationType, startTime, endTime, pageable);
        } else if (operatorId != null && startTime != null && endTime != null) {
            logPage = operationLogRepository.findByOperatorIdAndCreatedAtBetween(
                    operatorId, startTime, endTime, pageable);
        } else if (startTime != null && endTime != null) {
            logPage = operationLogRepository.findByCreatedAtBetween(startTime, endTime, pageable);
        } else if (operatorId != null) {
            logPage = operationLogRepository.findByOperatorId(operatorId, pageable);
        } else if (operationType != null) {
            logPage = operationLogRepository.findByOperationType(operationType, pageable);
        } else if (targetType != null) {
            logPage = operationLogRepository.findByTargetType(targetType, pageable);
        } else {
            logPage = operationLogRepository.findAll(pageable);
        }

        List<OperationLogDTO> dtos = logPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return PageResponse.<OperationLogDTO>builder()
                .total(logPage.getTotalElements())
                .page(page)
                .size(pageSize)
                .totalPages(logPage.getTotalPages())
                .items(dtos)
                .build();
    }

    /**
     * 将 OperationLog 转换为 DTO
     */
    private OperationLogDTO convertToDTO(OperationLog log) {
        OperationLogDTO dto = OperationLogDTO.builder()
                .id(log.getId())
                .operatorId(log.getOperatorId())
                .operationType(log.getOperationType())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .operationDetails(log.getOperationDetails())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .createdAt(log.getCreatedAt())
                .build();

        // 填充操作人信息
        if (log.getOperatorId() != null) {
            userRepository.findById(log.getOperatorId()).ifPresent(user -> {
                dto.setOperatorUsername(user.getUsername());
                dto.setOperatorFullName(user.getFullName());
            });
        } else {
            dto.setOperatorUsername("系统");
            dto.setOperatorFullName("系统操作");
        }

        return dto;
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 如果是多级代理，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
