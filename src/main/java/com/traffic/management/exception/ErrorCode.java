package com.traffic.management.exception;

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCode {

    // 通用错误 (10xxx)
    SUCCESS(200, "操作成功"),
    SYSTEM_ERROR(10000, "系统错误"),
    PARAM_ERROR(10001, "参数错误"),
    NOT_FOUND(10002, "资源不存在"),
    
    // 认证授权错误 (40xxx)
    UNAUTHORIZED(40001, "未认证"),
    FORBIDDEN(40003, "无权限访问"),
    TOKEN_EXPIRED(40004, "令牌已过期"),
    TOKEN_INVALID(40005, "令牌无效"),
    LOGIN_FAILED(40006, "用户名或密码错误"),
    ACCOUNT_SUSPENDED(40007, "账户已被停用"),
    
    // 用户相关错误 (41xxx)
    USER_NOT_FOUND(41001, "用户不存在"),
    USERNAME_EXISTS(41002, "用户名已存在"),
    POLICE_NUMBER_EXISTS(41003, "警号已存在"),
    INVALID_USER_STATUS(41004, "无效的用户状态"),
    CANNOT_DELETE_ADMIN(41005, "不能删除管理员账号"),
    
    // 路口相关错误 (42xxx)
    INTERSECTION_NOT_FOUND(42001, "路口不存在"),
    
    // 信号灯相关错误 (43xxx)
    SIGNAL_CONFIG_NOT_FOUND(43001, "信号灯配置不存在"),
    SIGNAL_CONFIG_EXISTS(43002, "信号灯配置已存在"),
    INVALID_SIGNAL_MODE(43003, "无效的信号灯模式"),
    INVALID_DURATION(43004, "无效的时长配置");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
