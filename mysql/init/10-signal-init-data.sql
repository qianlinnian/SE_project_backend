-- ============================================
-- TrafficMind 信号灯配置初始化数据
-- ============================================
-- 文件说明：为路口 1 创建默认的四个方向信号灯配置 
-- 创建时间：2025-12-27
-- ============================================

USE traffic_mind;

-- 清空可能存在的旧数据（可选，首次部署可注释掉）
-- DELETE FROM intersection_directions WHERE intersection_id = 1;

-- 插入路口 1 的四个方向配置
INSERT INTO intersection_directions
(
    intersection_id,
    direction,
    direction_name,
    lane_count,
    has_turn_lane,

    -- 直行信号灯时长配置（秒）
    straight_red_duration,
    straight_yellow_duration,
    straight_green_duration,

    -- 左转信号灯时长配置（秒）
    left_turn_red_duration,
    left_turn_yellow_duration,
    left_turn_green_duration,

    -- 右转信号灯时长配置（秒）- 已废弃，设置为0
    right_turn_red_duration,
    right_turn_yellow_duration,
    right_turn_green_duration,

    -- 当前信号灯状态
    current_straight_phase,
    current_left_turn_phase,
    current_right_turn_phase,

    -- 剩余时间（秒）
    straight_phase_remaining,
    left_turn_phase_remaining,
    right_turn_phase_remaining,

    -- 优先级和权重
    priority_level,
    traffic_weight,

    -- 时间戳
    created_at,
    updated_at
)
VALUES
-- ========== 北向（NORTH）==========
-- 配置说明（总周期60秒）：
--   - 直行：红30秒 → 绿27秒 → 黄3秒（总60秒）
--   - 左转：红45秒 → 绿12秒 → 黄3秒（总60秒，错开直行） 
(
    1,                  -- intersection_id: 路口1
    'NORTH',           -- direction: 北向
    '北向',            -- direction_name
    3,                 -- lane_count: 3车道
    TRUE,              -- has_turn_lane: 有左转车道

    30, 3, 27,        -- 直行：红30 黄3 绿27 (总60秒)
    45, 3, 12,        -- 左转：红45 黄3 绿12 (总60秒)
    0, 0, 0,          -- 右转：已废弃

    'RED', 'RED', 'RED',  -- 当前状态：初始全红
    0, 0, 0,          -- 剩余时间
    1, 1.0,           -- 优先级1，权重1.0
    NOW(), NOW()
),

-- ========== 南向（SOUTH）==========
-- 配置说明：与北向相同（南北对称）
(
    1,
    'SOUTH',
    '南向',
    3,
    TRUE,

    30, 3, 27,        -- 直行：红30 黄3 绿27 (总60秒)
    45, 3, 12,        -- 左转：红45 黄3 绿12 (总60秒)
    0, 0, 0,          -- 右转：已废弃

    'RED', 'RED', 'RED',
    0, 0, 0,
    1, 1.0,
    NOW(), NOW()
),

-- ========== 东向（EAST）==========
-- 配置说明（总周期60秒）：
--   - 直行：红30秒 → 绿27秒 → 黄3秒（总60秒）
--   - 左转：红45秒 → 绿12秒 → 黄3秒（总60秒）
--   - 东西向与南北向错开，初始状态为绿灯
(
    1,
    'EAST',
    '东向',
    3,
    TRUE,

    30, 3, 27,        -- 直行：红30 黄3 绿27 (总60秒)
    45, 3, 12,        -- 左转：红45 黄3 绿12 (总60秒)
    0, 0, 0,          -- 右转：已废弃

    'GREEN', 'RED', 'RED',  -- 当前状态：直行绿灯
    27, 0, 0,         -- 直行绿灯剩余27秒
    1, 1.0,
    NOW(), NOW()
),

-- ========== 西向（WEST）==========
-- 配置说明：与东向相同（东西对称）
(
    1,
    'WEST',
    '西向',
    3,
    TRUE,

    30, 3, 27,        -- 直行：红30 黄3 绿27 (总60秒)
    45, 3, 12,        -- 左转：红45 黄3 绿12 (总60秒)
    0, 0, 0,          -- 右转：已废弃

    'GREEN', 'RED', 'RED',  -- 当前状态：直行绿灯
    27, 0, 0,
    1, 1.0,
    NOW(), NOW()
)
ON DUPLICATE KEY UPDATE
    -- 如果记录已存在，更新配置
    straight_red_duration = VALUES(straight_red_duration),
    straight_yellow_duration = VALUES(straight_yellow_duration),
    straight_green_duration = VALUES(straight_green_duration),
    left_turn_red_duration = VALUES(left_turn_red_duration),
    left_turn_yellow_duration = VALUES(left_turn_yellow_duration),
    left_turn_green_duration = VALUES(left_turn_green_duration),
    right_turn_red_duration = 0,
    right_turn_yellow_duration = 0,
    right_turn_green_duration = 0,
    updated_at = NOW();

-- 验证插入结果
SELECT
    intersection_id AS '路口ID',
    direction AS '方向',
    direction_name AS '方向名',
    CONCAT('红', straight_red_duration, 's 绿', straight_green_duration, 's 黄', straight_yellow_duration, 's = ',
           straight_red_duration + straight_green_duration + straight_yellow_duration, 's') AS '直行配置',
    CONCAT('红', left_turn_red_duration, 's 绿', left_turn_green_duration, 's 黄', left_turn_yellow_duration, 's = ',
           left_turn_red_duration + left_turn_green_duration + left_turn_yellow_duration, 's') AS '左转配置',
    current_straight_phase AS '直行当前',
    current_left_turn_phase AS '左转当前'
FROM intersection_directions
WHERE intersection_id = 1
ORDER BY
    FIELD(direction, 'NORTH', 'SOUTH', 'EAST', 'WEST');

-- 输出信息
SELECT '✅ 路口1信号灯配置初始化完成！' AS 'Status';
SELECT '配置说明：' AS 'Info';
SELECT '  - 所有方向统一60秒周期' AS 'Detail';
SELECT '  - 南北向（NORTH/SOUTH）：直行红30s→绿27s→黄3s，左转红45s→绿12s→黄3s' AS 'Detail';
SELECT '  - 东西向（EAST/WEST）：直行红30s→绿27s→黄3s，左转红45s→绿12s→黄3s' AS 'Detail';
SELECT '  - 初始状态：南北红灯，东西绿灯（错峰运行）' AS 'Detail';
SELECT '  - 右转信号灯已移除（不再使用）' AS 'Detail';
