-- ============================================================
-- 升级脚本: 添加待转区违规类型
-- 说明: 扩展 violations 表的 violation_type 枚举类型
-- 添加: WAITING_AREA_RED_ENTRY (待转区红灯进入)
--      WAITING_AREA_ILLEGAL_EXIT (待转区非法驶离)
-- 创建日期: 2025-12-30
-- ============================================================

USE traffic_mind;

-- 修改 violation_type 枚举，添加待转区违规类型
ALTER TABLE violations MODIFY COLUMN violation_type ENUM(
    'RED_LIGHT',                    -- 闯红灯
    'WRONG_WAY',                    -- 逆行
    'ILLEGAL_LANE',                 -- 违法变道
    'SPEEDING',                     -- 超速
    'PARKING_VIOLATION',            -- 停车违规
    'CROSS_SOLID_LINE',             -- 跨实线/压实线
    'ILLEGAL_TURN',                 -- 违法转弯
    'WAITING_AREA_RED_ENTRY',       -- 待转区红灯进入（新增）
    'WAITING_AREA_ILLEGAL_EXIT',    -- 待转区非法驶离（新增）
    'OTHER'                         -- 其他
) NOT NULL COMMENT '违章类型';

-- 验证修改
SELECT COLUMN_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'traffic_mind'
  AND TABLE_NAME = 'violations'
  AND COLUMN_NAME = 'violation_type';
