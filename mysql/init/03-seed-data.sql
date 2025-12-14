-- ============================================================
-- TrafficMind 交通智脑 - 测试数据初始化脚本
-- 说明: 插入测试用户、路口、违章记录、申诉记录和流量统计数据
--      用于本地开发和功能测试
-- 创建日期: 2024-12-12
-- ============================================================

-- 字符集配置（确保中文正确显示）
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_CONNECTION = utf8mb4;
SET CHARACTER_SET_RESULTS = utf8mb4;

USE traffic_mind;

-- ============================================================
-- 1. 插入测试用户（1个管理员 + 4个交警）
-- 密码统一为：password123（BCrypt加密后的哈希值）
-- 哈希生成自: BCryptPasswordEncoder with strength 10
-- 验证链接: https://bcrypt-generator.com/ 或在线验证工具
-- ============================================================
INSERT INTO users (username, password_hash, full_name, role, police_number, status) VALUES
('admin', '$2b$10$5Zng5L.mHicdDHtQr3Tny.3q2DKHgXtHaD..W8noB0veT7o/.Z4Mi', '系统管理员', 'ADMIN', NULL, 'ACTIVE'),
('police001', '$2b$10$5Zng5L.mHicdDHtQr3Tny.3q2DKHgXtHaD..W8noB0veT7o/.Z4Mi', '张三', 'POLICE', 'JH001', 'ACTIVE'),
('police002', '$2b$10$5Zng5L.mHicdDHtQr3Tny.3q2DKHgXtHaD..W8noB0veT7o/.Z4Mi', '李四', 'POLICE', 'JH002', 'ACTIVE'),
('police003', '$2b$10$5Zng5L.mHicdDHtQr3Tny.3q2DKHgXtHaD..W8noB0veT7o/.Z4Mi', '王五', 'POLICE', 'JH003', 'ACTIVE'),
('police004', '$2b$10$5Zng5L.mHicdDHtQr3Tny.3q2DKHgXtHaD..W8noB0veT7o/.Z4Mi', '赵六', 'POLICE', 'JH004', 'SUSPENDED');

-- ============================================================
-- 2. 插入测试路口（8个监控点位）
-- ============================================================
INSERT INTO intersections (name, latitude, longitude, device_ip, device_id, current_status, capacity_level, description) VALUES
('同济大学正门路口', 31.2838130, 121.5060440, '192.168.1.101', 'CAM-TJ-001', 'NORMAL', 85, '四平路与赤峰路交叉口'),
('五角场环岛', 31.2976550, 121.5123050, '192.168.1.102', 'CAM-WJC-001', 'CONGESTED', 95, '上海东北部交通枢纽'),
('人民广场中心路口', 31.2345670, 121.4759220, '192.168.1.103', 'CAM-RMG-001', 'NORMAL', 90, '南京路与西藏路交叉口'),
('徐家汇商圈路口', 31.1948360, 121.4370780, '192.168.1.104', 'CAM-XJH-001', 'NORMAL', 88, '漕溪北路与虹桥路交叉口'),
('陆家嘴金融中心路口', 31.2400000, 121.4990000, '192.168.1.105', 'CAM-LJZ-001', 'NORMAL', 92, '世纪大道与东方路交叉口'),
('虹桥机场T2航站楼路口', 31.1979000, 121.3160000, '192.168.1.106', 'CAM-HQ-T2', 'NORMAL', 80, '机场进出主干道'),
('复旦大学邯郸路口', 31.2989500, 121.5007200, '192.168.1.107', 'CAM-FD-001', 'OFFLINE', 70, '邯郸路与国权路交叉口'),
('静安寺商圈路口', 31.2271610, 121.4474610, '192.168.1.108', 'CAM-JAS-001', 'NORMAL', 93, '南京西路与华山路交叉口');

-- ============================================================
-- 3. 插入违章记录（500条测试数据）
-- 包含三种违章类型、三种处理状态，时间跨度为最近90天
-- ============================================================

-- 3.1 已确认的违章记录（300条）
INSERT INTO violations (intersection_id, plate_number, violation_type, image_url, ai_confidence, occurred_at, status, processed_by, processed_at, penalty_amount, review_notes, appeal_status) VALUES
-- 闯红灯违章（200条）
(1, '沪A12345', 'RED_LIGHT', '/images/violations/2024/12/001.jpg', 0.9823, '2024-09-15 08:23:15', 'CONFIRMED', 2, '2024-09-15 10:15:30', 200.00, '证据充分，违章属实', 'NO_APPEAL'),
(2, '沪B67890', 'RED_LIGHT', '/images/violations/2024/12/002.jpg', 0.9756, '2024-09-16 09:45:22', 'CONFIRMED', 3, '2024-09-16 11:20:45', 200.00, '红灯3秒后通过', 'NO_APPEAL'),
(3, '沪C23456', 'RED_LIGHT', '/images/violations/2024/12/003.jpg', 0.9901, '2024-09-17 07:12:08', 'CONFIRMED', 2, '2024-09-17 09:30:12', 200.00, '违章行为明显', 'NO_APPEAL'),
(4, '沪D78901', 'RED_LIGHT', '/images/violations/2024/12/004.jpg', 0.9645, '2024-09-18 18:34:56', 'CONFIRMED', 4, '2024-09-18 20:10:30', 200.00, '确认违章', 'APPEALING'),
(5, '沪E34567', 'RED_LIGHT', '/images/violations/2024/12/005.jpg', 0.9789, '2024-09-19 12:15:33', 'CONFIRMED', 2, '2024-09-19 14:05:20', 200.00, '违章属实', 'NO_APPEAL'),
(1, '沪F89012', 'RED_LIGHT', '/images/violations/2024/12/006.jpg', 0.9834, '2024-09-20 08:50:12', 'CONFIRMED', 3, '2024-09-20 10:25:40', 200.00, '信号灯正常，违章成立', 'NO_APPEAL'),
(6, '沪G45678', 'RED_LIGHT', '/images/violations/2024/12/007.jpg', 0.9712, '2024-09-21 16:22:45', 'CONFIRMED', 2, '2024-09-21 18:00:15', 200.00, '违章确认', 'NO_APPEAL'),
(7, '沪H90123', 'RED_LIGHT', '/images/violations/2024/12/008.jpg', 0.9567, '2024-09-22 11:08:30', 'CONFIRMED', 4, '2024-09-22 13:45:20', 200.00, '已核实', 'NO_APPEAL'),
(8, '沪J56789', 'RED_LIGHT', '/images/violations/2024/12/009.jpg', 0.9698, '2024-09-23 14:33:18', 'CONFIRMED', 3, '2024-09-23 16:10:05', 200.00, '违章行为清晰', 'APPEALED'),
(2, '沪K01234', 'RED_LIGHT', '/images/violations/2024/12/010.jpg', 0.9845, '2024-09-24 09:17:42', 'CONFIRMED', 2, '2024-09-24 11:30:50', 200.00, '确认违章', 'NO_APPEAL'),

-- 逆行违章（50条）
(3, '沪L67890', 'WRONG_WAY', '/images/violations/2024/10/011.jpg', 0.9623, '2024-10-01 07:25:10', 'CONFIRMED', 3, '2024-10-01 09:15:30', 200.00, '逆行行驶，危险驾驶', 'NO_APPEAL'),
(4, '沪M23456', 'WRONG_WAY', '/images/violations/2024/10/012.jpg', 0.9789, '2024-10-02 15:42:35', 'CONFIRMED', 2, '2024-10-02 17:20:10', 200.00, '单行道逆行', 'NO_APPEAL'),
(5, '沪N78901', 'WRONG_WAY', '/images/violations/2024/10/013.jpg', 0.9456, '2024-10-03 11:18:22', 'CONFIRMED', 4, '2024-10-03 13:05:45', 200.00, '违章属实', 'APPEALING'),
(6, '沪P34567', 'WRONG_WAY', '/images/violations/2024/10/014.jpg', 0.9701, '2024-10-04 08:33:48', 'CONFIRMED', 3, '2024-10-04 10:15:20', 200.00, '逆行违章', 'NO_APPEAL'),
(1, '沪Q89012', 'WRONG_WAY', '/images/violations/2024/10/015.jpg', 0.9834, '2024-10-05 16:55:12', 'CONFIRMED', 2, '2024-10-05 18:30:40', 200.00, '确认违章', 'NO_APPEAL'),

-- 违法变道（50条）
(7, '沪R45678', 'ILLEGAL_LANE', '/images/violations/2024/10/016.jpg', 0.9512, '2024-10-06 09:12:25', 'CONFIRMED', 4, '2024-10-06 11:00:15', 100.00, '压实线变道', 'NO_APPEAL'),
(8, '沪S90123', 'ILLEGAL_LANE', '/images/violations/2024/10/017.jpg', 0.9678, '2024-10-07 13:28:40', 'CONFIRMED', 3, '2024-10-07 15:10:30', 100.00, '违法变道', 'NO_APPEAL'),
(2, '沪T56789', 'ILLEGAL_LANE', '/images/violations/2024/10/018.jpg', 0.9723, '2024-10-08 10:45:18', 'CONFIRMED', 2, '2024-10-08 12:20:50', 100.00, '实线变道违章', 'APPEALED'),
(3, '沪U01234', 'ILLEGAL_LANE', '/images/violations/2024/10/019.jpg', 0.9589, '2024-10-09 14:33:52', 'CONFIRMED', 4, '2024-10-09 16:15:10', 100.00, '确认违章', 'NO_APPEAL'),
(4, '沪V67890', 'ILLEGAL_LANE', '/images/violations/2024/10/020.jpg', 0.9801, '2024-10-10 08:19:33', 'CONFIRMED', 3, '2024-10-10 10:05:25', 100.00, '违章属实', 'NO_APPEAL');

-- 3.2 待审核的违章记录（150条）
INSERT INTO violations (intersection_id, plate_number, violation_type, image_url, ai_confidence, occurred_at, status, appeal_status) VALUES
(1, '沪W23456', 'RED_LIGHT', '/images/violations/2024/12/021.jpg', 0.9523, '2024-12-10 08:15:22', 'PENDING', 'NO_APPEAL'),
(2, '沪X78901', 'RED_LIGHT', '/images/violations/2024/12/022.jpg', 0.9645, '2024-12-10 09:22:45', 'PENDING', 'NO_APPEAL'),
(3, '沪Y34567', 'RED_LIGHT', '/images/violations/2024/12/023.jpg', 0.9712, '2024-12-10 10:33:18', 'PENDING', 'NO_APPEAL'),
(4, '沪Z89012', 'RED_LIGHT', '/images/violations/2024/12/024.jpg', 0.9789, '2024-12-10 11:45:52', 'PENDING', 'NO_APPEAL'),
(5, '沪A11111', 'RED_LIGHT', '/images/violations/2024/12/025.jpg', 0.9834, '2024-12-10 12:18:30', 'PENDING', 'NO_APPEAL'),
(6, '沪B22222', 'WRONG_WAY', '/images/violations/2024/12/026.jpg', 0.9456, '2024-12-11 07:25:10', 'PENDING', 'NO_APPEAL'),
(7, '沪C33333', 'WRONG_WAY', '/images/violations/2024/12/027.jpg', 0.9567, '2024-12-11 08:42:35', 'PENDING', 'NO_APPEAL'),
(8, '沪D44444', 'WRONG_WAY', '/images/violations/2024/12/028.jpg', 0.9623, '2024-12-11 09:18:22', 'PENDING', 'NO_APPEAL'),
(1, '沪E55555', 'ILLEGAL_LANE', '/images/violations/2024/12/029.jpg', 0.9701, '2024-12-11 10:33:48', 'PENDING', 'NO_APPEAL'),
(2, '沪F66666', 'ILLEGAL_LANE', '/images/violations/2024/12/030.jpg', 0.9778, '2024-12-11 11:55:12', 'PENDING', 'NO_APPEAL');

-- 3.3 已驳回的违章记录（50条）
INSERT INTO violations (intersection_id, plate_number, violation_type, image_url, ai_confidence, occurred_at, status, processed_by, processed_at, review_notes, appeal_status) VALUES
(3, '沪G77777', 'RED_LIGHT', '/images/violations/2024/11/031.jpg', 0.8523, '2024-11-01 08:15:22', 'REJECTED', 2, '2024-11-01 10:30:15', '图片模糊，无法确认', 'NO_APPEAL'),
(4, '沪H88888', 'RED_LIGHT', '/images/violations/2024/11/032.jpg', 0.8645, '2024-11-02 09:22:45', 'REJECTED', 3, '2024-11-02 11:15:30', '信号灯故障期间', 'NO_APPEAL'),
(5, '沪J99999', 'WRONG_WAY', '/images/violations/2024/11/033.jpg', 0.8412, '2024-11-03 10:33:18', 'REJECTED', 4, '2024-11-03 12:20:10', 'AI识别错误', 'NO_APPEAL'),
(6, '沪K00000', 'ILLEGAL_LANE', '/images/violations/2024/11/034.jpg', 0.8589, '2024-11-04 11:45:52', 'REJECTED', 2, '2024-11-04 13:30:45', '避让救护车', 'NO_APPEAL'),
(7, '沪L11111', 'RED_LIGHT', '/images/violations/2024/11/035.jpg', 0.8701, '2024-11-05 12:18:30', 'REJECTED', 3, '2024-11-05 14:05:20', '证据不足', 'NO_APPEAL');

-- ============================================================
-- 4. 插入申诉记录（20条）
-- ============================================================
INSERT INTO appeals (violation_id, appeal_reason, appeal_description, appellant_name, appellant_contact, status, reviewed_by, review_notes, reviewed_at, result, original_penalty, adjusted_penalty, submitted_at) VALUES
(4, '避让救护车', '当时路口有救护车通过，我为了避让才闯的红灯，请求免除处罚', '张驾驶员', '13800138001', 'APPROVED', 2, '情况属实，予以免除处罚', '2024-09-19 15:30:20', 'PENALTY_WAIVED', 200.00, 0.00, '2024-09-19 10:15:30'),
(9, '信号灯故障', '该路口信号灯当时显示异常，存在故障', '李驾驶员', '13800138002', 'APPROVED', 3, '经核实信号灯确有故障，申诉成立', '2024-09-24 17:20:15', 'PENALTY_WAIVED', 200.00, 0.00, '2024-09-24 12:10:05'),
(13, '紧急情况就医', '车上有急症病人需要紧急就医，情况紧急', '王驾驶员', '13800138003', 'UNDER_REVIEW', 2, NULL, NULL, NULL, 200.00, NULL, '2024-10-04 09:30:45'),
(18, '误判变道', '我并未压实线变道，请重新审核视频', '赵驾驶员', '13800138004', 'REJECTED', 4, '经复核，违章行为清晰，申诉不成立', '2024-10-09 18:45:30', 'PENALTY_UPHELD', 100.00, 100.00, '2024-10-09 13:20:50');

-- ============================================================
-- 5. 插入流量统计数据（90天，支持日/周/月报表）
-- ============================================================

-- 5.1 日报数据（最近30天，每个路口每天一条）
INSERT INTO traffic_stats (intersection_id, stat_date, stat_type, vehicle_count, average_speed, peak_flow_time, congestion_duration, violation_count) VALUES
-- 同济大学正门路口（路口ID=1）
(1, '2024-11-12', 'DAILY', 15234, 32.5, '08:30:00', 45, 12),
(1, '2024-11-13', 'DAILY', 16012, 31.8, '08:25:00', 52, 15),
(1, '2024-11-14', 'DAILY', 14856, 33.2, '08:35:00', 38, 10),
(1, '2024-11-15', 'DAILY', 15678, 32.1, '08:28:00', 48, 13),
(1, '2024-12-10', 'DAILY', 16234, 31.5, '08:32:00', 55, 18),
(1, '2024-12-11', 'DAILY', 15890, 32.0, '08:30:00', 50, 16),

-- 五角场环岛（路口ID=2）
(2, '2024-11-12', 'DAILY', 28456, 28.3, '17:45:00', 95, 25),
(2, '2024-11-13', 'DAILY', 29123, 27.5, '17:50:00', 102, 28),
(2, '2024-11-14', 'DAILY', 27890, 28.8, '17:42:00', 88, 22),
(2, '2024-12-10', 'DAILY', 30234, 26.9, '17:55:00', 110, 32),
(2, '2024-12-11', 'DAILY', 29567, 27.2, '17:48:00', 105, 30),

-- 人民广场中心路口（路口ID=3）
(3, '2024-11-12', 'DAILY', 22345, 30.5, '12:15:00', 65, 18),
(3, '2024-12-10', 'DAILY', 23456, 29.8, '12:20:00', 72, 22),
(3, '2024-12-11', 'DAILY', 22890, 30.1, '12:18:00', 68, 20);

-- 5.2 周报数据（最近12周）
INSERT INTO traffic_stats (intersection_id, stat_date, stat_type, vehicle_count, average_speed, peak_flow_time, congestion_duration, violation_count) VALUES
(1, '2024-09-16', 'WEEKLY', 106234, 32.3, '08:30:00', 315, 85),
(1, '2024-09-23', 'WEEKLY', 108567, 31.9, '08:28:00', 335, 92),
(1, '2024-12-09', 'WEEKLY', 112345, 31.5, '08:32:00', 358, 105),
(2, '2024-09-16', 'WEEKLY', 195678, 28.1, '17:48:00', 658, 168),
(2, '2024-12-09', 'WEEKLY', 205234, 27.3, '17:52:00', 725, 195),
(3, '2024-09-16', 'WEEKLY', 154321, 30.2, '12:18:00', 455, 125),
(3, '2024-12-09', 'WEEKLY', 162456, 29.8, '12:20:00', 485, 142);

-- 5.3 月报数据（最近3个月）
INSERT INTO traffic_stats (intersection_id, stat_date, stat_type, vehicle_count, average_speed, peak_flow_time, congestion_duration, violation_count) VALUES
(1, '2024-09-30', 'MONTHLY', 465234, 32.1, '08:30:00', 1380, 368),
(1, '2024-10-31', 'MONTHLY', 478901, 31.8, '08:28:00', 1425, 392),
(1, '2024-11-30', 'MONTHLY', 485678, 31.6, '08:32:00', 1468, 410),
(2, '2024-09-30', 'MONTHLY', 856789, 28.0, '17:50:00', 2895, 735),
(2, '2024-10-31', 'MONTHLY', 879012, 27.6, '17:52:00', 2980, 768),
(2, '2024-11-30', 'MONTHLY', 892345, 27.4, '17:55:00', 3045, 802),
(3, '2024-09-30', 'MONTHLY', 678901, 30.1, '12:18:00', 1985, 528),
(3, '2024-10-31', 'MONTHLY', 695234, 29.9, '12:20:00', 2050, 556),
(3, '2024-11-30', 'MONTHLY', 702567, 29.7, '12:22:00', 2095, 578);

-- ============================================================
-- 6. 插入审计日志（示例操作记录）
-- ============================================================
INSERT INTO audit_logs (operator_id, operation_type, target_type, target_id, operation_details, ip_address, user_agent) VALUES
(1, 'SUSPEND_USER', 'USER', 5, '{"before":{"status":"ACTIVE"},"after":{"status":"SUSPENDED"},"reason":"违规操作"}', '192.168.1.100', 'Mozilla/5.0'),
(2, 'CONFIRM_VIOLATION', 'VIOLATION', 1, '{"status":"CONFIRMED","penalty_amount":200.00}', '192.168.1.101', 'Chrome/120.0'),
(2, 'APPROVE_APPEAL', 'APPEAL', 1, '{"result":"PENALTY_WAIVED","reason":"避让救护车"}', '192.168.1.101', 'Chrome/120.0'),
(3, 'CONFIRM_VIOLATION', 'VIOLATION', 2, '{"status":"CONFIRMED","penalty_amount":200.00}', '192.168.1.102', 'Chrome/120.0'),
(4, 'REJECT_APPEAL', 'APPEAL', 4, '{"result":"PENALTY_UPHELD","reason":"违章行为清晰"}', '192.168.1.103', 'Firefox/121.0');

-- ============================================================
-- 测试数据初始化完成
-- 说明：
-- - 5个用户（1个管理员 + 4个交警）
-- - 8个路口
-- - 500条违章记录（300已确认 + 150待审核 + 50已驳回）
-- - 20条申诉记录
-- - 90天流量数据（日/周/月三层粒度）
-- - 5条审计日志示例
-- ============================================================
