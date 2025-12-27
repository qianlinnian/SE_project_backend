-- 数据库升级脚本：增加直行绿灯和转弯绿灯配置
-- 执行时间：2024-12-19

-- 添加直行和转弯信号灯的独立时长配置
ALTER TABLE signal_configs
ADD COLUMN straight_red_duration INT DEFAULT 30 COMMENT '直行红灯时长（秒）'
AFTER red_duration,
ADD COLUMN straight_yellow_duration INT DEFAULT 3 COMMENT '直行黄灯时长（秒）'
AFTER straight_red_duration,
ADD COLUMN straight_green_duration INT DEFAULT 25 COMMENT '直行绿灯时长（秒）'
AFTER straight_yellow_duration,
ADD COLUMN turn_red_duration INT DEFAULT 45 COMMENT '转弯红灯时长（秒）'
AFTER straight_green_duration,
ADD COLUMN turn_yellow_duration INT DEFAULT 3 COMMENT '转弯黄灯时长（秒）'
AFTER turn_red_duration,
ADD COLUMN turn_green_duration INT DEFAULT 15 COMMENT '转弯绿灯时长（秒）'
AFTER turn_yellow_duration;

-- 更新现有记录：初始化直行和转弯信号灯的独立配置
UPDATE signal_configs
SET
    straight_red_duration = red_duration, -- 直行红灯时长等于原红灯时长
    straight_yellow_duration = yellow_duration, -- 直行黄灯时长等于原黄灯时长
    straight_green_duration = FLOOR(green_duration * 0.6), -- 60%作为直行绿灯
    turn_red_duration = red_duration + FLOOR(green_duration * 0.6) + yellow_duration, -- 转弯红灯=直行周期时长
    turn_yellow_duration = 3, -- 转弯黄灯固定3秒
    turn_green_duration = green_duration - FLOOR(green_duration * 0.6) -- 40%作为转弯绿灯
WHERE
    straight_green_duration IS NULL;

-- 更新current_phase枚举类型，增加所有新的信号灯阶段
ALTER TABLE signal_configs MODIFY COLUMN current_phase ENUM(
    'RED',
    'YELLOW',
    'GREEN',
    'STRAIGHT_RED',
    'STRAIGHT_YELLOW',
    'STRAIGHT_GREEN',
    'TURN_RED',
    'TURN_YELLOW',
    'TURN_GREEN'
) NOT NULL DEFAULT 'STRAIGHT_GREEN' COMMENT '当前信号灯阶段';

-- 将现有的GREEN状态更新为STRAIGHT_GREEN
UPDATE signal_configs
SET
    current_phase = 'STRAIGHT_GREEN'
WHERE
    current_phase = 'GREEN';