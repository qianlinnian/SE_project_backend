package com.traffic.management.service;

import com.traffic.management.dto.request.PoliceCreateRequest;
import com.traffic.management.dto.request.UserStatusUpdateRequest;
import com.traffic.management.dto.response.PageResponse;
import com.traffic.management.entity.User;
import com.traffic.management.exception.BusinessException;
import com.traffic.management.exception.ErrorCode;
import com.traffic.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 创建交警账号
     */
    @Transactional
    public User createPolice(PoliceCreateRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // 检查警号是否已存在
        if (request.getPoliceNumber() != null && 
            userRepository.existsByPoliceNumber(request.getPoliceNumber())) {
            throw new BusinessException(ErrorCode.POLICE_NUMBER_EXISTS);
        }

        // 创建用户
        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .policeNumber(request.getPoliceNumber())
                .role(User.UserRole.POLICE)
                .status(User.UserStatus.ACTIVE)
                .build();

        user = userRepository.save(user);
        log.info("创建交警账号成功: {}", user.getUsername());

        return user;
    }

    /**
     * 分页查询交警列表
     */
    public PageResponse<User> listPolice(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());
        Page<User> userPage = userRepository.findByRole(User.UserRole.POLICE, pageable);

        return PageResponse.<User>builder()
                .total(userPage.getTotalElements())
                .page(page)
                .size(size)
                .totalPages(userPage.getTotalPages())
                .items(userPage.getContent())
                .build();
    }

    /**
     * 更新账号状态
     */
    @Transactional
    public User updateUserStatus(Long userId, UserStatusUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        try {
            User.UserStatus newStatus = User.UserStatus.valueOf(request.getStatus().toUpperCase());
            user.setStatus(newStatus);
            user = userRepository.save(user);
            log.info("更新用户 {} 状态为 {}", user.getUsername(), newStatus);
            return user;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_USER_STATUS);
        }
    }

    /**
     * 根据ID查询用户
     */
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 防止删除管理员账号
        if (user.getRole() == User.UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_ADMIN);
        }

        userRepository.delete(user);
        log.info("删除用户成功: {} (ID: {})", user.getUsername(), userId);
    }
}
