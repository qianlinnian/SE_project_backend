package com.traffic.management.controller;

import com.traffic.management.dto.request.LoginRequest;
import com.traffic.management.dto.response.ApiResponse;
import com.traffic.management.dto.response.LoginResponse;
import com.traffic.management.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        authService.logout(userId);
        return ApiResponse.success("登出成功", null);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<LoginResponse.UserInfo> getCurrentUser(Authentication authentication) {
        // 这里简化处理，实际应该从UserService获取完整信息
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.success("获取成功", null); // TODO: 实现完整的用户信息获取
    }
}
