package com.traffic.management.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String dbHash = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        String password = "password123";
        
        boolean matches = encoder.matches(password, dbHash);
        System.out.println("Password matches: " + matches);
        
        // 生成新的哈希用于对比
        String newHash = encoder.encode(password);
        System.out.println("New hash for password123: " + newHash);
    }
}
