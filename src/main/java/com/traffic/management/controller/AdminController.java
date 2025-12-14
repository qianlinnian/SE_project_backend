package com.traffic.management.controller;

import com.traffic.management.dto.request.PoliceCreateRequest;
import com.traffic.management.dto.request.UserStatusUpdateRequest;
import com.traffic.management.dto.response.ApiResponse;
import com.traffic.management.dto.response.PageResponse;
import com.traffic.management.entity.User;
import com.traffic.management.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员控制器
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    /**
     * 创建交警账号
     */
    @PostMapping("/police")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<User> createPolice(@Valid @RequestBody PoliceCreateRequest request) {
        User user = userService.createPolice(request);
        return ApiResponse.success("创建成功", user);
    }

    /**
     * 获取交警列表（分页）
     */
    @GetMapping("/police")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<User>> listPolice(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        PageResponse<User> response = userService.listPolice(page, size);
        return ApiResponse.success(response);
    }

    /**
     * 更新账号状态
     */
    @PutMapping("/police/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<User> updatePoliceStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        User user = userService.updateUserStatus(id, request);
        return ApiResponse.success("更新成功", user);
    }
}
