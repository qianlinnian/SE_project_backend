-- ============================================================
-- TrafficMind 交通智脑 - 索引优化脚本
-- 说明: 本脚本在01-schema.sql基础上添加额外的性能优化索引
--      主要针对高频查询场景和JOIN操作进行优化
-- 创建日期: 2024-12-12
-- ============================================================

-- 字符集配置（确保中文正确显示）
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;
SET CHARACTER_SET_RESULTS = utf8mb4;

USE traffic_mind;

-- ============================================================
-- 说明：01-schema.sql中已包含基础索引，此脚本主要添加复合索引和全文索引
-- ============================================================

-- violations表性能优化索引
-- 场景：按路口+状态查询待审核违章
CREATE INDEX idx_intersection_status ON violations(intersection_id, status);

-- 场景：按日期范围+违章类型统计
CREATE INDEX idx_occurred_type ON violations(occurred_at, violation_type);

-- 场景：按车牌号+日期查询历史违章记录
CREATE INDEX idx_plate_occurred ON violations(plate_number, occurred_at);

-- appeals表性能优化索引
-- 场景：按违章ID+申诉状态快速查找
CREATE INDEX idx_violation_status ON appeals(violation_id, status);

-- traffic_stats表性能优化索引
-- 场景：按路口+统计类型+日期范围生成报表
CREATE INDEX idx_intersection_type_date ON traffic_stats(intersection_id, stat_type, stat_date);

-- audit_logs表性能优化索引
-- 场景：按操作人+时间范围查询操作历史
CREATE INDEX idx_operator_created ON audit_logs(operator_id, created_at);

-- 场景：按操作类型+目标类型分组统计
CREATE INDEX idx_operation_target ON audit_logs(operation_type, target_type);

-- ============================================================
-- 全文索引（可选，用于模糊搜索场景）
-- 注意：全文索引会增加写入开销，根据实际需求启用
-- ============================================================

-- violations表：支持按审核备注进行全文搜索
-- ALTER TABLE violations ADD FULLTEXT INDEX ft_review_notes (review_notes);

-- appeals表：支持按申诉原因和描述进行全文搜索
-- ALTER TABLE appeals ADD FULLTEXT INDEX ft_appeal_content (appeal_reason, appeal_description);

-- ============================================================
-- 索引优化完成
-- 注意：索引会提高查询性能但会降低写入性能，需根据实际业务场景平衡
-- ============================================================
