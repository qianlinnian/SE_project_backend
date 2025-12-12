-- ============================================================
-- TrafficMind 交通智脑 - 核心业务数据库表结构
-- 数据库版本: MySQL 8.0
-- 创建日期: 2024-12-12
-- 说明: 本脚本创建6个核心业务表，支持用户管理、违章处理、申诉流程和流量统计
-- ============================================================

-- 字符集配置（确保中文正确显示）
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;
SET CHARACTER_SET_RESULTS = utf8mb4;

USE traffic_mind;

-- ============================================================
-- 表1: users - 用户账户表
-- 说明: 存储系统管理员和交警的账户信息，支持简化权限模型（ADMIN/POLICE）
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID，主键',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名，唯一',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希值（BCrypt加密）',
    full_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    role ENUM('ADMIN', 'POLICE') NOT NULL COMMENT '用户角色：ADMIN-管理员，POLICE-交警',
    police_number VARCHAR(20) UNIQUE COMMENT '警号（仅交警角色使用）',
    status ENUM('ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '账户状态：ACTIVE-活跃，SUSPENDED-停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_status (status),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账户表';

-- ============================================================
-- 表2: intersections - 交通路口基础信息表
-- 说明: 存储被监控的路口静态信息及关联设备
-- ============================================================
CREATE TABLE IF NOT EXISTS intersections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '路口ID，主键',
    name VARCHAR(100) NOT NULL COMMENT '路口名称',
    latitude DECIMAL(10, 7) NOT NULL COMMENT '纬度（精确到7位小数，约1.1cm精度）',
    longitude DECIMAL(10, 7) NOT NULL COMMENT '经度（精确到7位小数）',
    device_ip VARCHAR(45) COMMENT '关联设备IP地址（支持IPv4和IPv6）',
    device_id VARCHAR(50) COMMENT '设备唯一标识符',
    current_status ENUM('NORMAL', 'CONGESTED', 'OFFLINE') NOT NULL DEFAULT 'NORMAL' COMMENT '当前状态：NORMAL-正常，CONGESTED-拥堵，OFFLINE-离线',
    capacity_level INT DEFAULT 100 COMMENT '路口容量等级（1-100，用于流量分析）',
    description TEXT COMMENT '路口描述信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_name (name),
    INDEX idx_status (current_status),
    INDEX idx_location (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交通路口基础信息表';

-- ============================================================
-- 表3: violations - 违章记录表（核心业务表）
-- 说明: 记录AI识别的违章行为，支持完整的处理流程和申诉状态
-- ============================================================
CREATE TABLE IF NOT EXISTS violations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '违章记录ID，主键',
    intersection_id BIGINT NOT NULL COMMENT '路口ID，外键关联intersections表',
    plate_number VARCHAR(20) NOT NULL COMMENT '车牌号',
    violation_type ENUM('RED_LIGHT', 'WRONG_WAY', 'ILLEGAL_LANE') NOT NULL COMMENT '违章类型：RED_LIGHT-闯红灯，WRONG_WAY-逆行，ILLEGAL_LANE-违法变道',
    image_url VARCHAR(255) NOT NULL COMMENT '违章抓拍图片URL（OSS外部链接，暂无OSS时可为占位符）',
    ai_confidence FLOAT(5, 4) COMMENT 'AI识别置信度（0-1之间，如0.9523表示95.23%置信度）',
    occurred_at DATETIME NOT NULL COMMENT '违章发生时间',
    status ENUM('PENDING', 'CONFIRMED', 'REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING-待审核，CONFIRMED-已确认，REJECTED-已驳回',
    processed_by BIGINT COMMENT '处理该违章的交警ID，外键关联users表',
    processed_at DATETIME COMMENT '处理时间',
    penalty_amount DECIMAL(10, 2) COMMENT '罚款金额（元）',
    review_notes TEXT COMMENT '交警审核备注',
    appeal_status ENUM('NO_APPEAL', 'APPEALING', 'APPEALED') NOT NULL DEFAULT 'NO_APPEAL' COMMENT '申诉状态：NO_APPEAL-未申诉，APPEALING-申诉中，APPEALED-已申诉完成',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (intersection_id) REFERENCES intersections(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (processed_by) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_plate_number (plate_number),
    INDEX idx_status (status),
    INDEX idx_occurred_at (occurred_at),
    INDEX idx_intersection_occurred (intersection_id, occurred_at),
    INDEX idx_appeal_status (appeal_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='违章记录表';

-- ============================================================
-- 表4: appeals - 申诉记录表
-- 说明: 管理违章申诉流程，每个违章记录最多对应一条申诉记录
-- ============================================================
CREATE TABLE IF NOT EXISTS appeals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '申诉记录ID，主键',
    violation_id BIGINT NOT NULL UNIQUE COMMENT '违章记录ID，外键关联violations表（唯一约束）',
    appeal_reason VARCHAR(200) NOT NULL COMMENT '申诉原因简述',
    appeal_description TEXT COMMENT '详细申诉说明',
    appellant_name VARCHAR(50) COMMENT '申诉人姓名',
    appellant_contact VARCHAR(100) COMMENT '申诉人联系方式（手机/邮箱）',
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申诉提交时间',
    status ENUM('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'SUBMITTED' COMMENT '申诉状态：SUBMITTED-已提交，UNDER_REVIEW-审核中，APPROVED-已批准，REJECTED-已驳回',
    reviewed_by BIGINT COMMENT '审核人ID，外键关联users表',
    review_notes TEXT COMMENT '审核意见',
    reviewed_at DATETIME COMMENT '审核时间',
    result ENUM('PENALTY_WAIVED', 'PENALTY_REDUCED', 'PENALTY_UPHELD') COMMENT '申诉结果：PENALTY_WAIVED-罚款免除，PENALTY_REDUCED-罚款降低，PENALTY_UPHELD-罚款维持',
    original_penalty DECIMAL(10, 2) COMMENT '原罚款金额',
    adjusted_penalty DECIMAL(10, 2) COMMENT '调整后罚款金额',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (violation_id) REFERENCES violations(id) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_status (status),
    INDEX idx_submitted_at (submitted_at),
    INDEX idx_reviewed_by (reviewed_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='申诉记录表';

-- ============================================================
-- 表5: traffic_stats - 交通流量统计表
-- 说明: 支持多粒度（日/周/月）的流量数据存储，用于报表生成和趋势分析
-- ============================================================
CREATE TABLE IF NOT EXISTS traffic_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '统计记录ID，主键',
    intersection_id BIGINT NOT NULL COMMENT '路口ID，外键关联intersections表',
    stat_date DATE NOT NULL COMMENT '统计日期',
    stat_type ENUM('DAILY', 'WEEKLY', 'MONTHLY') NOT NULL COMMENT '统计粒度：DAILY-日报，WEEKLY-周报，MONTHLY-月报',
    vehicle_count INT NOT NULL DEFAULT 0 COMMENT '车流量（车辆数）',
    average_speed FLOAT(5, 2) COMMENT '平均车速（km/h）',
    peak_flow_time TIME COMMENT '高峰期时段（如：08:30:00）',
    congestion_duration INT DEFAULT 0 COMMENT '拥堵时长（分钟）',
    violation_count INT DEFAULT 0 COMMENT '违章次数',
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '数据生成时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_intersection_date_type (intersection_id, stat_date, stat_type) COMMENT '同一路口同一天同一粒度唯一',
    FOREIGN KEY (intersection_id) REFERENCES intersections(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_stat_date (stat_date),
    INDEX idx_stat_type (stat_type),
    INDEX idx_recorded_at (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交通流量统计表';

-- ============================================================
-- 表6: audit_logs - 审计日志表
-- 说明: 记录系统关键操作，用于追溯权限变更、违章处理、申诉审核等业务操作
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID，主键',
    operator_id BIGINT COMMENT '操作人ID，外键关联users表（NULL表示系统操作）',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型（如：CREATE_USER, SUSPEND_USER, CONFIRM_VIOLATION, APPROVE_APPEAL）',
    target_type VARCHAR(50) NOT NULL COMMENT '操作目标类型（如：USER, VIOLATION, APPEAL）',
    target_id BIGINT NOT NULL COMMENT '操作目标ID',
    operation_details JSON COMMENT '操作详情（JSON格式，存储操作前后的数据对比）',
    ip_address VARCHAR(45) COMMENT '操作IP地址',
    user_agent VARCHAR(255) COMMENT '用户代理信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    FOREIGN KEY (operator_id) REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_operator_id (operator_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_target (target_type, target_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- ============================================================
-- 数据库初始化完成
-- ============================================================
