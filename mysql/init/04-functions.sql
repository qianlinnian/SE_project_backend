-- ============================================================
-- TrafficMind 交通智脑 - 触发器与存储过程
-- 说明: 自动化业务逻辑，减少应用层代码复杂度
-- 创建日期: 2024-12-12
-- ============================================================

-- 字符集配置（确保中文正确显示）
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;
SET CHARACTER_SET_RESULTS = utf8mb4;

USE traffic_mind;

-- ============================================================
-- 触发器1: 违章记录更新时自动同步申诉状态
-- 说明: 当违章状态变为CONFIRMED时，如果有申诉记录则更新申诉状态
-- ============================================================
DELIMITER $$

CREATE TRIGGER trg_violation_status_update
AFTER UPDATE ON violations
FOR EACH ROW
BEGIN
    -- 当违章从PENDING变为CONFIRMED时，检查是否需要初始化申诉状态
    IF OLD.status = 'PENDING' AND NEW.status = 'CONFIRMED' THEN
        -- 这里可以添加自动通知逻辑
        INSERT INTO audit_logs (operator_id, operation_type, target_type, target_id, operation_details, created_at)
        VALUES (NEW.processed_by, 'CONFIRM_VIOLATION', 'VIOLATION', NEW.id, 
                JSON_OBJECT('plate_number', NEW.plate_number, 'violation_type', NEW.violation_type, 'penalty_amount', NEW.penalty_amount),
                NOW());
    END IF;
    -- 当违章从PENDING变为REJECTED时，检查是否需要初始化申诉状态
    IF OLD.status = 'PENDING' AND NEW.status = 'REJECTED' THEN
        -- 这里可以添加自动通知逻辑
        INSERT INTO audit_logs (operator_id, operation_type, target_type, target_id, operation_details, created_at)
        VALUES (NEW.processed_by, 'REJECT_VIOLATION', 'VIOLATION', NEW.id, 
                JSON_OBJECT('plate_number', NEW.plate_number, 'violation_type', NEW.violation_type, 'penalty_amount', NEW.penalty_amount),
                NOW());
    END IF;
    
    -- 当申诉被批准时，更新违章的申诉状态
    IF OLD.appeal_status != NEW.appeal_status AND NEW.appeal_status = 'APPEALED' THEN
        INSERT INTO audit_logs (operator_id, operation_type, target_type, target_id, operation_details, created_at)
        VALUES (NEW.processed_by, 'UPDATE_APPEAL_STATUS', 'VIOLATION', NEW.id,
                JSON_OBJECT('old_status', OLD.appeal_status, 'new_status', NEW.appeal_status),
                NOW());
    END IF;
END$$

DELIMITER ;

-- ============================================================
-- 触发器2: 申诉记录更新时自动记录审计日志
-- 说明: 申诉状态变更时自动记录到审计日志
-- ============================================================
DELIMITER $$

CREATE TRIGGER trg_appeal_status_update
AFTER UPDATE ON appeals
FOR EACH ROW
BEGIN
    -- 申诉审核通过
    IF OLD.status != NEW.status AND NEW.status = 'APPROVED' THEN
        INSERT INTO audit_logs (operator_id, operation_type, target_type, target_id, operation_details, created_at)
        VALUES (NEW.reviewed_by, 'APPROVE_APPEAL', 'APPEAL', NEW.id,
                JSON_OBJECT('violation_id', NEW.violation_id, 'result', NEW.result, 'original_penalty', NEW.original_penalty, 'adjusted_penalty', NEW.adjusted_penalty),
                NOW());
                
        -- 同步更新违章记录的申诉状态
        UPDATE violations SET appeal_status = 'APPEALED' WHERE id = NEW.violation_id;
    END IF;
    
    -- 申诉被驳回
    IF OLD.status != NEW.status AND NEW.status = 'REJECTED' THEN
        INSERT INTO audit_logs (operator_id, operation_type, target_type, target_id, operation_details, created_at)
        VALUES (NEW.reviewed_by, 'REJECT_APPEAL', 'APPEAL', NEW.id,
                JSON_OBJECT('violation_id', NEW.violation_id, 'reason', NEW.review_notes),
                NOW());
                
        -- 同步更新违章记录的申诉状态
        UPDATE violations SET appeal_status = 'APPEALED' WHERE id = NEW.violation_id;
    END IF;
END$$

DELIMITER ;

-- ============================================================
-- 存储过程1: 批量生成日报数据
-- 说明: 从原始流量数据聚合生成每日统计报表
-- 参数: p_stat_date - 统计日期
-- ============================================================
DELIMITER $$

CREATE PROCEDURE sp_generate_daily_stats(IN p_stat_date DATE)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_intersection_id BIGINT;
    DECLARE cur CURSOR FOR SELECT id FROM intersections WHERE current_status != 'OFFLINE';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    OPEN cur;
    
    read_loop: LOOP
        FETCH cur INTO v_intersection_id;
        IF done THEN
            LEAVE read_loop;
        END IF;
        
        -- 检查是否已存在该日期的统计数据
        IF NOT EXISTS (SELECT 1 FROM traffic_stats 
                       WHERE intersection_id = v_intersection_id 
                       AND stat_date = p_stat_date 
                       AND stat_type = 'DAILY') THEN
            
            -- 插入日报数据（这里使用模拟数据，实际应从原始采集表聚合）
            INSERT INTO traffic_stats (intersection_id, stat_date, stat_type, vehicle_count, average_speed, peak_flow_time, congestion_duration, violation_count)
            VALUES (v_intersection_id, p_stat_date, 'DAILY', 
                    FLOOR(10000 + RAND() * 20000),  -- 模拟车流量
                    ROUND(25 + RAND() * 15, 2),     -- 模拟平均车速
                    '08:30:00',                      -- 模拟高峰时段
                    FLOOR(30 + RAND() * 90),        -- 模拟拥堵时长
                    FLOOR(5 + RAND() * 25));        -- 模拟违章次数
        END IF;
    END LOOP;
    
    CLOSE cur;
END$$

DELIMITER ;

-- ============================================================
-- 存储过程2: 批量生成周报数据
-- 说明: 从日报数据聚合生成每周统计报表
-- 参数: p_week_start_date - 周起始日期（周一）
-- ============================================================
DELIMITER $$

CREATE PROCEDURE sp_generate_weekly_stats(IN p_week_start_date DATE)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_intersection_id BIGINT;
    DECLARE v_week_end_date DATE;
    DECLARE cur CURSOR FOR SELECT id FROM intersections WHERE current_status != 'OFFLINE';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    SET v_week_end_date = DATE_ADD(p_week_start_date, INTERVAL 6 DAY);
    
    OPEN cur;
    
    read_loop: LOOP
        FETCH cur INTO v_intersection_id;
        IF done THEN
            LEAVE read_loop;
        END IF;
        
        -- 检查是否已存在该周的统计数据
        IF NOT EXISTS (SELECT 1 FROM traffic_stats 
                       WHERE intersection_id = v_intersection_id 
                       AND stat_date = p_week_start_date 
                       AND stat_type = 'WEEKLY') THEN
            
            -- 从日报聚合周报数据
            INSERT INTO traffic_stats (intersection_id, stat_date, stat_type, vehicle_count, average_speed, peak_flow_time, congestion_duration, violation_count)
            SELECT 
                v_intersection_id,
                p_week_start_date,
                'WEEKLY',
                SUM(vehicle_count),
                AVG(average_speed),
                '08:30:00',
                SUM(congestion_duration),
                SUM(violation_count)
            FROM traffic_stats
            WHERE intersection_id = v_intersection_id
              AND stat_date BETWEEN p_week_start_date AND v_week_end_date
              AND stat_type = 'DAILY'
            HAVING SUM(vehicle_count) > 0;
        END IF;
    END LOOP;
    
    CLOSE cur;
END$$

DELIMITER ;

-- ============================================================
-- 存储过程3: 批量生成月报数据
-- 说明: 从日报数据聚合生成每月统计报表
-- 参数: p_year - 年份, p_month - 月份
-- ============================================================
DELIMITER $$

CREATE PROCEDURE sp_generate_monthly_stats(IN p_year INT, IN p_month INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_intersection_id BIGINT;
    DECLARE v_start_date DATE;
    DECLARE v_end_date DATE;
    DECLARE cur CURSOR FOR SELECT id FROM intersections WHERE current_status != 'OFFLINE';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
    
    SET v_start_date = DATE(CONCAT(p_year, '-', LPAD(p_month, 2, '0'), '-01'));
    SET v_end_date = LAST_DAY(v_start_date);
    
    OPEN cur;
    
    read_loop: LOOP
        FETCH cur INTO v_intersection_id;
        IF done THEN
            LEAVE read_loop;
        END IF;
        
        -- 检查是否已存在该月的统计数据
        IF NOT EXISTS (SELECT 1 FROM traffic_stats 
                       WHERE intersection_id = v_intersection_id 
                       AND stat_date = v_end_date 
                       AND stat_type = 'MONTHLY') THEN
            
            -- 从日报聚合月报数据
            INSERT INTO traffic_stats (intersection_id, stat_date, stat_type, vehicle_count, average_speed, peak_flow_time, congestion_duration, violation_count)
            SELECT 
                v_intersection_id,
                v_end_date,
                'MONTHLY',
                SUM(vehicle_count),
                AVG(average_speed),
                '08:30:00',
                SUM(congestion_duration),
                SUM(violation_count)
            FROM traffic_stats
            WHERE intersection_id = v_intersection_id
              AND stat_date BETWEEN v_start_date AND v_end_date
              AND stat_type = 'DAILY'
            HAVING SUM(vehicle_count) > 0;
        END IF;
    END LOOP;
    
    CLOSE cur;
END$$

DELIMITER ;

-- ============================================================
-- 存储过程4: 查询路口违章统计
-- 说明: 查询指定路口在指定时间范围内的违章统计
-- 参数: p_intersection_id - 路口ID, p_start_date - 开始日期, p_end_date - 结束日期
-- ============================================================
DELIMITER $$

CREATE PROCEDURE sp_get_violation_stats(
    IN p_intersection_id BIGINT,
    IN p_start_date DATE,
    IN p_end_date DATE
)
BEGIN
    SELECT 
        violation_type,
        COUNT(*) AS violation_count,
        SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) AS confirmed_count,
        SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_count,
        SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count,
        SUM(CASE WHEN appeal_status = 'APPEALING' THEN 1 ELSE 0 END) AS appealing_count,
        SUM(penalty_amount) AS total_penalty
    FROM violations
    WHERE intersection_id = p_intersection_id
      AND DATE(occurred_at) BETWEEN p_start_date AND p_end_date
    GROUP BY violation_type;
END$$

DELIMITER ;

-- ============================================================
-- 函数1: 计算违章罚款金额
-- 说明: 根据违章类型自动计算罚款金额
-- 参数: p_violation_type - 违章类型
-- 返回: 罚款金额
-- ============================================================
DELIMITER $$

CREATE FUNCTION fn_calculate_penalty(p_violation_type VARCHAR(20))
RETURNS DECIMAL(10,2)
DETERMINISTIC
BEGIN
    DECLARE v_penalty DECIMAL(10,2);
    
    CASE p_violation_type
        WHEN 'RED_LIGHT' THEN SET v_penalty = 200.00;
        WHEN 'WRONG_WAY' THEN SET v_penalty = 200.00;
        WHEN 'ILLEGAL_LANE' THEN SET v_penalty = 100.00;
        ELSE SET v_penalty = 0.00;
    END CASE;
    
    RETURN v_penalty;
END$$

DELIMITER ;

-- ============================================================
-- 触发器和存储过程创建完成
-- 使用示例:
-- 1. 生成2024-12-12的日报: CALL sp_generate_daily_stats('2024-12-12');
-- 2. 生成某周的周报: CALL sp_generate_weekly_stats('2024-12-09');
-- 3. 生成2024年12月的月报: CALL sp_generate_monthly_stats(2024, 12);
-- 4. 查询路口违章统计: CALL sp_get_violation_stats(1, '2024-11-01', '2024-11-30');
-- 5. 计算违章罚款: SELECT fn_calculate_penalty('RED_LIGHT');
-- ============================================================
