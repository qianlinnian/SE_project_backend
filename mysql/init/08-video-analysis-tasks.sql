-- 视频分析任务管理系统升级脚本
-- 添加任务追踪、状态管理和AI集成支持

-- 1. 视频分析任务表
CREATE TABLE video_analysis_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) UNIQUE NOT NULL,
    intersection_id BIGINT NOT NULL,
    direction ENUM('EAST', 'SOUTH', 'WEST', 'NORTH') NOT NULL,
    video_file_path VARCHAR(500) NOT NULL,
    video_url VARCHAR(500) NOT NULL,

-- 任务状态管理
status ENUM(
    'UPLOADED',
    'AI_PROCESSING',
    'AI_COMPLETED',
    'AI_FAILED',
    'VALIDATION_COMPLETED',
    'FAILED'
) NOT NULL DEFAULT 'UPLOADED',
progress INTEGER DEFAULT 0,

-- 时间戳
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
ai_started_at TIMESTAMP NULL,
ai_completed_at TIMESTAMP NULL,
completed_at TIMESTAMP NULL,

-- AI相关信息
ai_callback_url VARCHAR(500),
ai_request_payload TEXT,
ai_error_message TEXT,
retry_count INTEGER DEFAULT 0,

-- 文件信息
original_filename VARCHAR(255),
file_size BIGINT,
duration_seconds INTEGER,

-- 结果统计

total_violations INTEGER DEFAULT 0,
    processed_violations INTEGER DEFAULT 0,
    
    INDEX idx_task_id (task_id),
    INDEX idx_status (status),
    INDEX idx_intersection_direction (intersection_id, direction),
    INDEX idx_created_at (created_at)
);

-- 2. AI检测结果原始数据表
CREATE TABLE ai_detection_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    result_id VARCHAR(36) UNIQUE NOT NULL,

-- AI原始结果
plate_number VARCHAR(20) NOT NULL,
violation_type VARCHAR(50) NOT NULL,
confidence DECIMAL(3, 2) NOT NULL,

-- 位置和时间信息
bounding_box JSON,
frame_timestamp DECIMAL(10, 3),
detection_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

-- 处理状态

processing_status ENUM('PENDING', 'VALIDATED', 'REJECTED', 'FAILED') DEFAULT 'PENDING',
    violation_id BIGINT NULL,
    rejection_reason VARCHAR(255),
    processed_at TIMESTAMP NULL,
    
    INDEX idx_task_id (task_id),
    INDEX idx_processing_status (processing_status),
    INDEX idx_detection_timestamp (detection_timestamp),
    
    INDEX idx_violation_id (violation_id),
    INDEX idx_task_id (task_id)
);

-- 3. 任务错误日志表
CREATE TABLE task_error_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    error_type ENUM(
        'AI_TIMEOUT',
        'AI_ERROR',
        'NETWORK_ERROR',
        'VALIDATION_ERROR',
        'SYSTEM_ERROR'
    ) NOT NULL,
    error_message TEXT NOT NULL,
    error_details JSON,
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    retry_attempted BOOLEAN DEFAULT FALSE,
    INDEX idx_task_id (task_id),
    INDEX idx_error_type (error_type),
    INDEX idx_occurred_at (occurred_at)
);

-- 4. AI集成配置表
CREATE TABLE ai_integration_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    description VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 5. 插入AI集成默认配置
INSERT INTO
    ai_integration_config (
        config_key,
        config_value,
        description
    )
VALUES (
        'ai.endpoint.base_url',
        'http://localhost:5000',
        'AI模型服务基础URL'
    ),
    (
        'ai.endpoint.analyze_video',
        '/analyze-video',
        'AI视频分析接口路径'
    ),
    (
        'ai.timeout.connect_seconds',
        '30',
        'AI连接超时时间（秒）'
    ),
    (
        'ai.timeout.read_seconds',
        '300',
        'AI读取超时时间（秒）'
    ),
    (
        'ai.retry.max_attempts',
        '3',
        'AI调用最大重试次数'
    ),
    (
        'ai.retry.delay_seconds',
        '10',
        'AI重试延迟时间（秒）'
    ),
    (
        'callback.base_url',
        'http://localhost:8088',
        '后端回调基础URL'
    ),
    (
        'callback.ai_result_path',
        '/api/violation-detection/ai-callback',
        'AI结果回调路径'
    );

-- 6. 为现有violations表添加任务关联字段
ALTER TABLE violations
ADD COLUMN task_id VARCHAR(36) NULL,
ADD COLUMN ai_result_id VARCHAR(36) NULL,
ADD INDEX idx_task_id (task_id);

-- 7. 创建任务状态变化记录表（用于追踪和调试）
CREATE TABLE task_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(100),
    notes TEXT,
    INDEX idx_task_id (task_id),
    INDEX idx_changed_at (changed_at)
);

-- 8. 创建用于WebSocket通知的事件表
CREATE TABLE notification_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    event_type ENUM(
        'TASK_CREATED',
        'AI_STARTED',
        'AI_COMPLETED',
        'VIOLATION_DETECTED',
        'TASK_COMPLETED',
        'ERROR_OCCURRED'
    ) NOT NULL,
    event_data JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    recipient VARCHAR(100),
    INDEX idx_task_id (task_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at),
    INDEX idx_sent_at (sent_at)
);

-- 9. 创建触发器：任务状态变化时自动记录历史
DELIMITER $$

CREATE TRIGGER task_status_change_trigger
    AFTER UPDATE ON video_analysis_tasks
    FOR EACH ROW
BEGIN
    IF OLD.status != NEW.status THEN
        INSERT INTO task_status_history (task_id, from_status, to_status, changed_by, notes)
        VALUES (NEW.task_id, OLD.status, NEW.status, 'SYSTEM', 
                CONCAT('Progress: ', COALESCE(NEW.progress, 0), '%'));
    END IF;
END$$

DELIMITER;

-- 10. 创建触发器：任务状态变化时自动创建通知事件
DELIMITER $$

CREATE TRIGGER task_notification_trigger
    AFTER UPDATE ON video_analysis_tasks
    FOR EACH ROW
BEGIN
    DECLARE event_type_val VARCHAR(50);
    
    IF OLD.status != NEW.status THEN
        SET event_type_val = CASE NEW.status
            WHEN 'AI_PROCESSING' THEN 'AI_STARTED'
            WHEN 'AI_COMPLETED' THEN 'AI_COMPLETED'
            WHEN 'VALIDATION_COMPLETED' THEN 'TASK_COMPLETED'
            WHEN 'AI_FAILED' THEN 'ERROR_OCCURRED'
            WHEN 'FAILED' THEN 'ERROR_OCCURRED'
            ELSE NULL
        END;
        
        IF event_type_val IS NOT NULL THEN
            INSERT INTO notification_events (task_id, event_type, event_data)
            VALUES (NEW.task_id, event_type_val, 
                    JSON_OBJECT('status', NEW.status, 'progress', NEW.progress, 
                               'message', COALESCE(NEW.ai_error_message, 'Status changed')));
        END IF;
    END IF;
END$$

DELIMITER;

-- 11. 创建视图：任务详细信息（包含统计）
CREATE VIEW task_details_view AS
SELECT
    t.id,
    t.task_id,
    t.intersection_id,
    i.name AS intersection_name,
    t.direction,
    t.status,
    t.progress,
    t.created_at,
    t.ai_started_at,
    t.ai_completed_at,
    t.completed_at,
    t.original_filename,
    t.file_size,
    t.duration_seconds,
    t.total_violations,
    t.processed_violations,
    t.retry_count,
    t.ai_error_message,
    -- 统计信息
    COALESCE(detected.detection_count, 0) AS ai_detection_count,
    COALESCE(validated.validated_count, 0) AS validated_violation_count,
    COALESCE(rejected.rejected_count, 0) AS rejected_detection_count,
    -- 计算处理时长
    CASE
        WHEN t.ai_completed_at IS NOT NULL
        AND t.ai_started_at IS NOT NULL THEN TIMESTAMPDIFF (
            SECOND,
            t.ai_started_at,
            t.ai_completed_at
        )
        ELSE NULL
    END AS ai_processing_duration_seconds
FROM
    video_analysis_tasks t
    LEFT JOIN intersections i ON t.intersection_id = i.id
    LEFT JOIN (
        SELECT task_id, COUNT(*) as detection_count
        FROM ai_detection_results
        GROUP BY
            task_id
    ) detected ON t.task_id = detected.task_id
    LEFT JOIN (
        SELECT task_id, COUNT(*) as validated_count
        FROM ai_detection_results
        WHERE
            processing_status = 'VALIDATED'
        GROUP BY
            task_id
    ) validated ON t.task_id = validated.task_id
    LEFT JOIN (
        SELECT task_id, COUNT(*) as rejected_count
        FROM ai_detection_results
        WHERE
            processing_status = 'REJECTED'
        GROUP BY
            task_id
    ) rejected ON t.task_id = rejected.task_id;