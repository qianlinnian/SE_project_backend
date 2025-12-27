package com.traffic.management.service;

import com.traffic.management.entity.User;
import com.traffic.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户数据初始化服务
 * 用于在应用启动时创建默认用户
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserInitializationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener
    public void onApplicationEvent(ContextRefreshedEvent event) {
        createDefaultUsers();
    }

    private void createDefaultUsers() {
        // 检查是否已存在admin用户
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(passwordEncoder.encode("password123"));
            admin.setFullName("系统管理员");
            admin.setRole(User.UserRole.ADMIN);
            admin.setStatus(User.UserStatus.ACTIVE);

            userRepository.save(admin);
            log.info("创建默认管理员用户: admin / password123");
        }

        // 创建测试交警用户
        if (userRepository.findByUsername("police01").isEmpty()) {
            User police = new User();
            police.setUsername("police01");
            police.setPasswordHash(passwordEncoder.encode("police123"));
            police.setFullName("测试交警");
            police.setPoliceNumber("PC001");
            police.setRole(User.UserRole.POLICE);
            police.setStatus(User.UserStatus.ACTIVE);

            userRepository.save(police);
            log.info("创建测试交警用户: police01 / police123");
        }
    }
}