-- ============================================================
-- TrafficMind 交通智脑 - 实时交通流历史记录表
-- 数据库版本: MySQL 8.0
-- 创建日期: 2024-12-26
-- 说明: 用于存储 LLM 推送的高频交通流快照数据 (Module 4)
--       本表为独立表，无外键关联，以支持高频写入和全量快照存储
-- ============================================================

USE traffic_mind;

-- ============================================================
-- 表: traffic_flow_records
-- ============================================================
CREATE TABLE IF NOT EXISTS traffic_flow_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID，主键',
    
    simulation_timestamp DOUBLE NOT NULL COMMENT '仿真环境时间戳(秒)',
    
    step INT COMMENT '仿真步数',
    
    control_mode VARCHAR(20) COMMENT '控制模式: ai 或 fixed',
    
    total_queue INT COMMENT '全路网总排队数(聚合值)',
    
    total_vehicles INT COMMENT '全路网总车辆数(聚合值)',
    
    full_data_snapshot JSON COMMENT '完整数据快照(包含所有路口、车道、单元格详情)',
    
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录入库时间',
    
    -- 索引优化
    -- 用于按仿真时间回放或查询
    INDEX idx_sim_timestamp (simulation_timestamp),
    
    -- 用于按入库时间查询最近记录
    INDEX idx_created_at (created_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实时交通流历史记录表';

-- ============================================================
-- 初始化完成
-- ============================================================