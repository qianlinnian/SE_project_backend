-- ============================================================
-- TrafficMind 交通智脑 - 信号灯控制表结构
-- 创建日期: 2024-12-13
-- 说明: 用于Module 4智能信号灯控制功能
-- ============================================================

USE traffic_mind;

-- ============================================================
-- 表: signal_configs - 信号灯配置表
-- 说明: 存储每个路口的信号灯配置信息和实时状态
-- ============================================================
CREATE TABLE IF NOT EXISTS signal_configs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID，主键',
    intersection_id BIGINT NOT NULL UNIQUE COMMENT '路口ID，外键关联intersections表，唯一',
    signal_mode ENUM('FIXED', 'INTELLIGENT', 'MANUAL') NOT NULL DEFAULT 'FIXED' COMMENT '信号灯模式：FIXED-固定配时，INTELLIGENT-智能调控，MANUAL-人工干预',
    green_duration INT NOT NULL DEFAULT 30 COMMENT '绿灯时长（秒）',
    red_duration INT NOT NULL DEFAULT 30 COMMENT '红灯时长（秒）',
    yellow_duration INT NOT NULL DEFAULT 3 COMMENT '黄灯时长（秒）',
    current_phase ENUM('RED', 'YELLOW', 'GREEN') NOT NULL DEFAULT 'GREEN' COMMENT '当前信号灯阶段',
    phase_remaining INT NOT NULL DEFAULT 0 COMMENT '当前阶段剩余秒数',
    cycle_time INT NOT NULL DEFAULT 63 COMMENT '完整信号周期时长（秒），红+绿+黄',
    last_adjusted_at DATETIME COMMENT '最后调整时间',
    last_adjusted_by BIGINT COMMENT '最后调整人ID，外键关联users表（NULL表示系统自动调整）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (intersection_id) REFERENCES intersections(id) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (last_adjusted_by) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_signal_mode (signal_mode),
    INDEX idx_intersection_id (intersection_id),
    INDEX idx_last_adjusted_at (last_adjusted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信号灯配置表';

-- ============================================================
-- 表: signal_logs - 信号灯操作日志表
-- 说明: 记录信号灯配置的所有调整历史，用于审计和分析
-- ============================================================
CREATE TABLE IF NOT EXISTS signal_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID，主键',
    intersection_id BIGINT NOT NULL COMMENT '路口ID，外键关联intersections表',
    action_type VARCHAR(50) NOT NULL COMMENT '操作类型：AUTO_ADJUST-自动调整，MANUAL_OVERRIDE-人工干预，MODE_CHANGE-模式切换',
    old_config JSON COMMENT '调整前配置（JSON格式）',
    new_config JSON NOT NULL COMMENT '调整后配置（JSON格式）',
    operator_id BIGINT COMMENT '操作人ID，外键关联users表（NULL表示系统操作）',
    reason VARCHAR(255) COMMENT '调整原因说明',
    ip_address VARCHAR(45) COMMENT '操作IP地址',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    FOREIGN KEY (intersection_id) REFERENCES intersections(id) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (operator_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_intersection_id (intersection_id),
    INDEX idx_action_type (action_type),
    INDEX idx_created_at (created_at),
    INDEX idx_operator_id (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信号灯操作日志表';
