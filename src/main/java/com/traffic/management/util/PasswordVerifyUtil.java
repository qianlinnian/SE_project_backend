package com.traffic.management.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt密码验证工具（仅用于调试）
 */
public class PasswordVerifyUtil {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 数据库中的密码哈希
        String storedHash = "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi";
        
        // 用户输入的密码
        String inputPassword = "password123";
        
        // 验证
        boolean isMatch = encoder.matches(inputPassword, storedHash);
        
        System.out.println("========== BCrypt密码验证 ==========");
        System.out.println("存储的哈希: " + storedHash);
        System.out.println("输入的密码: " + inputPassword);
        System.out.println("验证结果: " + (isMatch ? "匹配成功" : "匹配失败"));
        
        if (!isMatch) {
            System.out.println("\n尝试重新加密password123:");
            String newHash = encoder.encode(inputPassword);
            System.out.println("新生成的哈希: " + newHash);
            System.out.println("新验证结果: " + encoder.matches(inputPassword, newHash));
        }
    }
}
