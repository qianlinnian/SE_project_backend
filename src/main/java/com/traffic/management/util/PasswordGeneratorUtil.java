package com.traffic.management.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码生成工具 - 用于生成BCrypt密码哈希
 */
public class PasswordGeneratorUtil {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "password123";
        
        // 生成哈希
        String hash = encoder.encode(password);
        
        System.out.println("========== BCrypt密码哈希生成 ==========");
        System.out.println("原始密码: " + password);
        System.out.println("生成的哈希: " + hash);
        
        // 验证
        boolean matches = encoder.matches(password, hash);
        System.out.println("验证结果: " + (matches ? "匹配成功" : "匹配失败"));
        
        // 输出SQL语句
        System.out.println("\n复制下面的SQL语句到03-seed-data.sql:");
        System.out.println("UPDATE users SET password_hash = '" + hash + "' WHERE username IN ('admin', 'police001', 'police002', 'police003', 'police004');");
    }
}
