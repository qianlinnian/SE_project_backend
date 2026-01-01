package com.traffic.management.controller;

import com.traffic.management.dto.request.OperationLogRequest;
import com.traffic.management.dto.response.ApiResponse;
import com.traffic.management.dto.response.OperationLogDTO;
import com.traffic.management.dto.response.PageResponse;
import com.traffic.management.security.JwtTokenProvider;
import com.traffic.management.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 操作日志控制器
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 记录操作日志
     */
    @PostMapping("/logs")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> createLog(
            @Valid @RequestBody OperationLogRequest request,
            HttpServletRequest httpRequest) {

        // 从请求头获取当前用户ID
        String token = extractToken(httpRequest);
        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        operationLogService.log(userId, request, httpRequest);
        return ApiResponse.success("日志记录成功", null);
    }

    /**
     * 查询操作日志（分页）
     */
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<OperationLogDTO>> queryLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        PageResponse<OperationLogDTO> response = operationLogService.queryLogs(
                page, pageSize, operatorId, operationType, targetType, startTime, endTime);
        return ApiResponse.success(response);
    }

    /**
     * 从请求头提取 JWT Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
