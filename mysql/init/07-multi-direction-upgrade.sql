-- 数据库升级脚本：支持多路口四方向交通控制
-- 执行时间：2024-12-19

-- ============================================================
-- 新增：路口方向表 - intersection_directions
-- 说明：每个路口包含四个方向（东、南、西、北），每个方向有独立的信号灯控制
-- ============================================================
CREATE TABLE IF NOT EXISTS intersection_directions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '方向ID，主键',
    intersection_id BIGINT NOT NULL COMMENT '路口ID，外键关联intersections表',
    direction ENUM('EAST', 'SOUTH', 'WEST', 'NORTH') NOT NULL COMMENT '方向：EAST-东，SOUTH-南，WEST-西，NORTH-北',
    direction_name VARCHAR(50) NOT NULL COMMENT '方向中文名称，如"向东方向"',
    lane_count INT NOT NULL DEFAULT 3 COMMENT '该方向车道数量',
    has_turn_lane BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否有转弯车道',

-- 直行信号灯配置
straight_red_duration INT NOT NULL DEFAULT 60 COMMENT '直行红灯时长（秒）',
straight_yellow_duration INT NOT NULL DEFAULT 3 COMMENT '直行黄灯时长（秒）',
straight_green_duration INT NOT NULL DEFAULT 40 COMMENT '直行绿灯时长（秒）',

-- 左转信号灯配置
left_turn_red_duration INT NOT NULL DEFAULT 80 COMMENT '左转红灯时长（秒）',
left_turn_yellow_duration INT NOT NULL DEFAULT 3 COMMENT '左转黄灯时长（秒）',
left_turn_green_duration INT NOT NULL DEFAULT 20 COMMENT '左转绿灯时长（秒）',

-- 右转信号灯配置
right_turn_red_duration INT NOT NULL DEFAULT 80 COMMENT '右转红灯时长（秒）',
right_turn_yellow_duration INT NOT NULL DEFAULT 3 COMMENT '右转黄灯时长（秒）',
right_turn_green_duration INT NOT NULL DEFAULT 20 COMMENT '右转绿灯时长（秒）',

-- 当前状态
current_straight_phase ENUM('RED', 'YELLOW', 'GREEN') NOT NULL DEFAULT 'RED' COMMENT '当前直行信号灯阶段',
current_left_turn_phase ENUM('RED', 'YELLOW', 'GREEN') NOT NULL DEFAULT 'RED' COMMENT '当前左转信号灯阶段',
current_right_turn_phase ENUM('RED', 'YELLOW', 'GREEN') NOT NULL DEFAULT 'RED' COMMENT '当前右转信号灯阶段',

-- 相位剩余时间
straight_phase_remaining INT NOT NULL DEFAULT 0 COMMENT '直行当前阶段剩余秒数',
left_turn_phase_remaining INT NOT NULL DEFAULT 0 COMMENT '左转当前阶段剩余秒数',
right_turn_phase_remaining INT NOT NULL DEFAULT 0 COMMENT '右转当前阶段剩余秒数',

-- 优先级和权重
priority_level INT NOT NULL DEFAULT 1 COMMENT '方向优先级（1-10，用于智能调配）',
traffic_weight DECIMAL(3, 2) NOT NULL DEFAULT 1.00 COMMENT '交通权重系数（0.1-3.0）',

-- 设备关联
camera_id VARCHAR(50) COMMENT '该方向监控摄像头ID',
detector_id VARCHAR(50) COMMENT '车辆检测器ID',

-- 时间戳
created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

-- 约束和索引
UNIQUE KEY uk_intersection_direction (intersection_id, direction) COMMENT '同一路口同一方向唯一',
    FOREIGN KEY (intersection_id) REFERENCES intersections(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_intersection_id (intersection_id),
    INDEX idx_direction (direction),
    INDEX idx_priority (priority_level),
    INDEX idx_camera (camera_id),
    INDEX idx_detector (detector_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='路口方向信号灯配置表';

-- ============================================================
-- 新增：交通相位配置表 - traffic_phases
-- 说明：定义路口整体的交通相位配置，协调各方向的信号灯时序
-- ============================================================
CREATE TABLE IF NOT EXISTS traffic_phases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '相位ID，主键',
    intersection_id BIGINT NOT NULL COMMENT '路口ID，外键关联intersections表',
    phase_name VARCHAR(50) NOT NULL COMMENT '相位名称，如"相位一：南北直行"',
    phase_sequence INT NOT NULL COMMENT '相位序号（1-8，表示执行顺序）',

-- 参与该相位的方向和行为
east_straight BOOLEAN NOT NULL DEFAULT FALSE COMMENT '东向直行是否参与此相位',
east_left_turn BOOLEAN NOT NULL DEFAULT FALSE COMMENT '东向左转是否参与此相位',
east_right_turn BOOLEAN NOT NULL DEFAULT FALSE COMMENT '东向右转是否参与此相位',
south_straight BOOLEAN NOT NULL DEFAULT FALSE COMMENT '南向直行是否参与此相位',
south_left_turn BOOLEAN NOT NULL DEFAULT FALSE COMMENT '南向左转是否参与此相位',
south_right_turn BOOLEAN NOT NULL DEFAULT FALSE COMMENT '南向右转是否参与此相位',
west_straight BOOLEAN NOT NULL DEFAULT FALSE COMMENT '西向直行是否参与此相位',
west_left_turn BOOLEAN NOT NULL DEFAULT FALSE COMMENT '西向左转是否参与此相位',
west_right_turn BOOLEAN NOT NULL DEFAULT FALSE COMMENT '西向右转是否参与此相位',
north_straight BOOLEAN NOT NULL DEFAULT FALSE COMMENT '北向直行是否参与此相位',
north_left_turn BOOLEAN NOT NULL DEFAULT FALSE COMMENT '北向左转是否参与此相位',
north_right_turn BOOLEAN NOT NULL DEFAULT FALSE COMMENT '北向右转是否参与此相位',

-- 相位时长配置
green_duration INT NOT NULL COMMENT '该相位绿灯时长（秒）',
yellow_duration INT NOT NULL DEFAULT 3 COMMENT '该相位黄灯时长（秒）',
all_red_duration INT NOT NULL DEFAULT 2 COMMENT '全红时间（秒，用于清空路口）',

-- 相位状态
is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用该相位',
current_status ENUM(
    'WAITING',
    'GREEN',
    'YELLOW',
    'ALL_RED'
) NOT NULL DEFAULT 'WAITING' COMMENT '当前状态',
created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

-- 约束和索引
UNIQUE KEY uk_intersection_sequence (intersection_id, phase_sequence) COMMENT '同一路口相位序号唯一',
    FOREIGN KEY (intersection_id) REFERENCES intersections(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_intersection_id (intersection_id),
    INDEX idx_phase_sequence (phase_sequence),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交通相位配置表';

-- ============================================================
-- 修改违章表，增加方向信息
-- ============================================================
ALTER TABLE violations
ADD COLUMN direction ENUM(
    'EAST',
    'SOUTH',
    'WEST',
    'NORTH'
) COMMENT '违章发生方向'
AFTER intersection_id,
ADD COLUMN turn_type ENUM(
    'STRAIGHT',
    'LEFT_TURN',
    'RIGHT_TURN',
    'U_TURN'
) COMMENT '行驶类型：直行、左转、右转、掉头'
AFTER direction,
ADD INDEX idx_direction (direction),
ADD INDEX idx_turn_type (turn_type),
ADD INDEX idx_intersection_direction (intersection_id, direction);

-- ============================================================
-- 初始化数据：为现有路口创建四个方向
-- ============================================================

-- 为每个现有路口自动创建四个方向的配置
INSERT INTO
    intersection_directions (
        intersection_id,
        direction,
        direction_name,
        lane_count,
        has_turn_lane,
        straight_red_duration,
        straight_green_duration,
        left_turn_red_duration,
        left_turn_green_duration,
        right_turn_red_duration,
        right_turn_green_duration,
        priority_level,
        traffic_weight
    )
SELECT i.id, 'EAST', '向东方向', 3, TRUE, 60, 40, 80, 20, 80, 20, 2, 1.0
FROM intersections i
UNION ALL
SELECT i.id, 'SOUTH', '向南方向', 3, TRUE, 60, 40, 80, 20, 80, 20, 1, 1.2
FROM intersections i
UNION ALL
SELECT i.id, 'WEST', '向西方向', 3, TRUE, 60, 40, 80, 20, 80, 20, 2, 1.0
FROM intersections i
UNION ALL
SELECT i.id, 'NORTH', '向北方向', 3, TRUE, 60, 40, 80, 20, 80, 20, 1, 1.2
FROM intersections i;

-- ============================================================
-- 初始化标准四相位配置
-- ============================================================

-- 为每个路口创建标准四相位配置
INSERT INTO
    traffic_phases (
        intersection_id,
        phase_name,
        phase_sequence,
        south_straight,
        north_straight,
        green_duration,
        yellow_duration,
        all_red_duration
    )
SELECT i.id, '相位一：南北直行', 1, TRUE, TRUE, 40, 3, 2
FROM intersections i;

INSERT INTO
    traffic_phases (
        intersection_id,
        phase_name,
        phase_sequence,
        south_left_turn,
        north_left_turn,
        green_duration,
        yellow_duration,
        all_red_duration
    )
SELECT i.id, '相位二：南北左转', 2, TRUE, TRUE, 20, 3, 2
FROM intersections i;

INSERT INTO
    traffic_phases (
        intersection_id,
        phase_name,
        phase_sequence,
        east_straight,
        west_straight,
        green_duration,
        yellow_duration,
        all_red_duration
    )
SELECT i.id, '相位三：东西直行', 3, TRUE, TRUE, 35, 3, 2
FROM intersections i;

INSERT INTO
    traffic_phases (
        intersection_id,
        phase_name,
        phase_sequence,
        east_left_turn,
        west_left_turn,
        green_duration,
        yellow_duration,
        all_red_duration
    )
SELECT i.id, '相位四：东西左转', 4, TRUE, TRUE, 18, 3, 2
FROM intersections i;