package com.traffic.management.controller;

import com.traffic.management.dto.request.SignalAdjustRequest;
import com.traffic.management.dto.response.ApiResponse;
import com.traffic.management.dto.response.SignalConfigResponse;
import com.traffic.management.service.SignalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 信号灯控制器
 */
@RestController
@RequestMapping("/api/signals")
@RequiredArgsConstructor
public class SignalController {

    private final SignalService signalService;

    /**
     * 调整信号灯配置
     */
    @PostMapping("/{id}/adjust")
    @PreAuthorize("hasAnyRole('ADMIN', 'POLICE')")
    public ApiResponse<SignalConfigResponse> adjustSignal(
            @PathVariable Long id,
            @Valid @RequestBody SignalAdjustRequest request,
            Authentication authentication) {
        Long operatorId = (Long) authentication.getPrincipal();
        SignalConfigResponse response = signalService.adjustSignal(id, request, operatorId);
        return ApiResponse.success("调整成功", response);
    }

    /**
     * 获取信号灯配置
     */
    @GetMapping("/{id}/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'POLICE')")
    public ApiResponse<SignalConfigResponse> getSignalConfig(@PathVariable Long id) {
        SignalConfigResponse response = signalService.getSignalConfig(id);
        return ApiResponse.success(response);
    }

    /**
     * 获取所有路口信号灯配置
     */
    @GetMapping("")
    @PreAuthorize("hasAnyRole('ADMIN', 'POLICE')")
    public ApiResponse<List<SignalConfigResponse>> getAllSignalConfigs() {
        List<SignalConfigResponse> response = signalService.getAllSignalConfigs();
        return ApiResponse.success(response);
    }
}
