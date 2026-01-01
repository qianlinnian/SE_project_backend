package com.traffic.management.service;

import com.traffic.management.dto.request.LoginRequest;
import com.traffic.management.dto.response.LoginResponse;
import com.traffic.management.entity.User;
import com.traffic.management.exception.BusinessException;
import com.traffic.management.exception.ErrorCode;
import com.traffic.management.repository.UserRepository;
import com.traffic.management.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_CACHE_PREFIX = "user:";
    private static final String TOKEN_CACHE_SUFFIX = ":token";
    private static final long TOKEN_CACHE_TTL = 7; // 7天

    /**
     * 用户登录
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        // 查询用户
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    log.error("用户不存在: {}", request.getUsername());
                    return new BusinessException(ErrorCode.LOGIN_FAILED);
                });

        log.debug("找到用户: {}, 密码哈希: {}", user.getUsername(), user.getPasswordHash());

        // 验证密码
        boolean passwordMatch = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        log.debug("密码验证结果: {}", passwordMatch);

        if (!passwordMatch) {
            log.error("密码验证失败，用户: {}, 输入密码: {}", user.getUsername(), request.getPassword());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 检查账号状态
        if (user.getStatus() == User.UserStatus.SUSPENDED) {
            throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED);
        }

        // 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userRepository.save(user);

        // 生成Token
        String token = jwtTokenProvider.generateToken(user);

        // 存入Redis缓存
        String cacheKey = TOKEN_CACHE_PREFIX + user.getId() + TOKEN_CACHE_SUFFIX;
        redisTemplate.opsForValue().set(cacheKey, token, TOKEN_CACHE_TTL, TimeUnit.DAYS);

        log.info("用户 {} 登录成功", user.getUsername());

        // 构造响应
        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("bearer")
                .userInfo(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole().name())
                        .fullName(user.getFullName())
                        .build())
                .build();
    }

    /**
     * 登出
     */
    public void logout(Long userId) {
        String cacheKey = TOKEN_CACHE_PREFIX + userId + TOKEN_CACHE_SUFFIX;
        redisTemplate.delete(cacheKey);
        log.info("用户 {} 已登出", userId);
    }
}
