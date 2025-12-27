-- ============================================================
-- 升级脚本: 添加新的违章类型
-- 说明: 扩展 violations 表的 violation_type 枚举类型
-- 添加: CROSS_SOLID_LINE (跨实线), ILLEGAL_TURN (违法转弯)
-- 同时添加 direction 和 turn_type 字段
-- ============================================================

USE traffic_mind;

-- 修改 violation_type 枚举，添加新类型
ALTER TABLE violations MODIFY COLUMN violation_type ENUM(
    'RED_LIGHT', -- 闯红灯
    'WRONG_WAY', -- 逆行
    'ILLEGAL_LANE', -- 违法变道
    'SPEEDING', -- 超速
    'PARKING_VIOLATION', -- 停车违规
    'CROSS_SOLID_LINE', -- 跨实线/压实线
    'ILLEGAL_TURN', -- 违法转弯
    'OTHER' -- 其他
) NOT NULL COMMENT '违章类型';

-- 添加方向字段（如果不存在）
-- 检查并添加 direction 列
SET
    @ exist := (
        SELECT COUNT(*)
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE
            TABLE_SCHEMA = 'traffic_mind'
            AND TABLE_NAME = 'violations'
            AND COLUMN_NAME = 'direction'
    );

SET
    @ sql := IF (
        @ exist = 0,
        'ALTER TABLE violations ADD COLUMN direction ENUM(''EAST'', ''SOUTH'', ''WEST'', ''NORTH'') NULL COMMENT ''方向：EAST-东，SOUTH-南，WEST-西，NORTH-北'' AFTER intersection_id',
        'SELECT ''direction column already exists'''
    );

PREPARE stmt FROM @ sql;

EXECUTE stmt;

DEALLOCATE PREPARE stmt;

-- 检查并添加 turn_type 列
SET
    @ exist2 := (
        SELECT COUNT(*)
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE
            TABLE_SCHEMA = 'traffic_mind'
            AND TABLE_NAME = 'violations'
            AND COLUMN_NAME = 'turn_type'
    );

SET
    @ sql2 := IF (
        @ exist2 = 0,
        'ALTER TABLE violations ADD COLUMN turn_type ENUM(''STRAIGHT'', ''LEFT_TURN'', ''RIGHT_TURN'', ''U_TURN'') NULL COMMENT ''转弯类型：STRAIGHT-直行，LEFT_TURN-左转，RIGHT_TURN-右转，U_TURN-掉头'' AFTER direction',
        'SELECT ''turn_type column already exists'''
    );

PREPARE stmt2 FROM @ sql2;

EXECUTE stmt2;

DEALLOCATE PREPARE stmt2;