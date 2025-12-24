# TrafficMind 交通智脑 - 数据库表字段说明

## 📋 完整表清单

1. **users** - 用户账户表
2. **intersections** - 交通路口基础信息表
3. **violations** - 违章记录表（核心）
4. **appeals** - 申诉记录表
5. **traffic_stats** - 交通流量统计表
6. **audit_logs** - 审计日志表

---

## 1. users - 用户账户表

### 业务说明
存储系统管理员和交警的账户信息，支持简化的二级权限模型（ADMIN/POLICE）。

### 字段详解

| 字段名 | 类型 | 约束 | 说明 | 业务规则 |
|-------|------|------|------|---------|
| id | BIGINT | PK, AUTO_INCREMENT | 用户ID | 系统自动生成 |
| username | VARCHAR(50) | UNIQUE, NOT NULL | 用户名 | 登录凭证，唯一 |
| password_hash | VARCHAR(255) | NOT NULL | 密码哈希 | BCrypt加密，不存储明文 |
| full_name | VARCHAR(50) | NOT NULL | 真实姓名 | 用于显示和审计 |
| role | ENUM('ADMIN', 'POLICE') | NOT NULL | 用户角色 | ADMIN-管理员，POLICE-交警 |
| police_number | VARCHAR(20) | UNIQUE, NULL | 警号 | 仅POLICE角色使用，ADMIN为NULL |
| status | ENUM('ACTIVE', 'SUSPENDED') | DEFAULT 'ACTIVE' | 账户状态 | ACTIVE-正常，SUSPENDED-停用 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 | 自动记录 |
| updated_at | DATETIME | AUTO UPDATE | 更新时间 | 任何修改自动更新 |

### 业务逻辑
- **权限控制**: ADMIN可查看所有路口和报表，POLICE仅查看负责区域
- **状态管理**: SUSPENDED状态的用户无法登录系统
- **警号唯一性**: 同一警号不能分配给多个交警

---

## 2. intersections - 交通路口基础信息表

### 业务说明
存储被监控的路口静态信息，包括地理位置、关联设备等。

### 字段详解

| 字段名 | 类型 | 约束 | 说明 | 业务规则 |
|-------|------|------|------|---------|
| id | BIGINT | PK, AUTO_INCREMENT | 路口ID | 系统自动生成 |
| name | VARCHAR(100) | NOT NULL | 路口名称 | 如"同济大学正门路口" |
| latitude | DECIMAL(10,7) | NOT NULL | 纬度 | 精确到7位小数（约1.1cm精度） |
| longitude | DECIMAL(10,7) | NOT NULL | 经度 | 精确到7位小数 |
| device_ip | VARCHAR(45) | NULL | 设备IP | 支持IPv4/IPv6，关联摄像头 |
| device_id | VARCHAR(50) | NULL | 设备唯一标识 | 如"CAM-TJ-001" |
| current_status | ENUM('NORMAL', 'CONGESTED', 'OFFLINE') | DEFAULT 'NORMAL' | 当前状态 | 实时更新路口状态 |
| capacity_level | INT | DEFAULT 100 | 容量等级 | 1-100，用于流量分析 |
| description | TEXT | NULL | 路口描述 | 补充说明信息 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 | 自动记录 |
| updated_at | DATETIME | AUTO UPDATE | 更新时间 | 任何修改自动更新 |

### 业务逻辑
- **地理位置**: 用于地图展示和热力图生成
- **设备关联**: 通过device_ip/device_id关联边缘计算设备
- **状态缓存**: current_status用于快速查询，避免频繁计算

---

## 3. violations - 违章记录表（核心业务表）

### 业务说明
记录AI识别的违章行为，支持完整的处理流程（待审核→已确认/已驳回）和申诉状态。

### 字段详解

| 字段名 | 类型 | 约束 | 说明 | 业务规则 |
|-------|------|------|------|---------|
| id | BIGINT | PK, AUTO_INCREMENT | 违章记录ID | 系统自动生成 |
| intersection_id | BIGINT | FK, NOT NULL | 路口ID | 关联intersections表 |
| plate_number | VARCHAR(20) | NOT NULL | 车牌号 | 如"沪A·12345" |
| violation_type | ENUM | NOT NULL | 违章类型 | RED_LIGHT-闯红灯，WRONG_WAY-逆行，ILLEGAL_LANE-违法变道 |
| image_url | VARCHAR(255) | NOT NULL | 图片URL | 存储OSS外部链接，暂无OSS时为占位符 |
| ai_confidence | FLOAT(5,4) | NULL | AI置信度 | 0-1之间，如0.9523表示95.23% |
| occurred_at | DATETIME | NOT NULL | 违章时间 | 实际发生时间 |
| status | ENUM | DEFAULT 'PENDING' | 处理状态 | PENDING-待审核，CONFIRMED-已确认，REJECTED-已驳回 |
| processed_by | BIGINT | FK, NULL | 处理人ID | 关联users表，NULL表示未处理 |
| processed_at | DATETIME | NULL | 处理时间 | 审核完成时间 |
| penalty_amount | DECIMAL(10,2) | NULL | 罚款金额 | 单位：元 |
| review_notes | TEXT | NULL | 审核备注 | 交警审核意见 |
| appeal_status | ENUM | DEFAULT 'NO_APPEAL' | 申诉状态 | NO_APPEAL-未申诉，APPEALING-申诉中，APPEALED-已完成申诉 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 | AI识别时间 |
| updated_at | DATETIME | AUTO UPDATE | 更新时间 | 任何修改自动更新 |

### 业务逻辑
- **处理流程**: PENDING → 交警审核 → CONFIRMED/REJECTED
- **罚款标准**: 闯红灯200元，逆行200元，违法变道100元
- **申诉链路**: 只有CONFIRMED状态的违章才能申诉
- **永久保存**: 所有记录永久保存，不删除

### 重要索引
- `idx_status` - 快速过滤待审核违章
- `idx_plate_number` - 按车牌查询历史
- `idx_intersection_occurred` - 按路口+时间统计

---

## 4. appeals - 申诉记录表

### 业务说明
管理违章申诉流程，一个违章记录最多对应一条申诉记录（1:1关系）。

### 字段详解

| 字段名 | 类型 | 约束 | 说明 | 业务规则 |
|-------|------|------|------|---------|
| id | BIGINT | PK, AUTO_INCREMENT | 申诉记录ID | 系统自动生成 |
| violation_id | BIGINT | FK, UNIQUE, NOT NULL | 违章记录ID | 关联violations表，唯一约束 |
| appeal_reason | VARCHAR(200) | NOT NULL | 申诉原因 | 简述原因 |
| appeal_description | TEXT | NULL | 详细说明 | 申诉详细描述 |
| appellant_name | VARCHAR(50) | NULL | 申诉人姓名 | 可与车主不同 |
| appellant_contact | VARCHAR(100) | NULL | 联系方式 | 手机号或邮箱 |
| submitted_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 提交时间 | 申诉创建时间 |
| status | ENUM | DEFAULT 'SUBMITTED' | 申诉状态 | SUBMITTED-已提交，UNDER_REVIEW-审核中，APPROVED-已批准，REJECTED-已驳回 |
| reviewed_by | BIGINT | FK, NULL | 审核人ID | 关联users表 |
| review_notes | TEXT | NULL | 审核意见 | 交警/管理员审核备注 |
| reviewed_at | DATETIME | NULL | 审核时间 | 审核完成时间 |
| result | ENUM | NULL | 申诉结果 | PENALTY_WAIVED-罚款免除，PENALTY_REDUCED-罚款降低，PENALTY_UPHELD-罚款维持 |
| original_penalty | DECIMAL(10,2) | NULL | 原罚款金额 | 申诉前金额 |
| adjusted_penalty | DECIMAL(10,2) | NULL | 调整后金额 | 申诉后金额 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 | 自动记录 |
| updated_at | DATETIME | AUTO UPDATE | 更新时间 | 任何修改自动更新 |

### 业务逻辑
- **申诉时机**: 仅CONFIRMED状态的违章可申诉
- **一次申诉**: 每个违章只能申诉一次（UNIQUE约束）
- **结果类型**: 免除、降低、维持三种结果
- **状态同步**: 申诉审核完成后，自动更新violations表的appeal_status

---

## 5. traffic_stats - 交通流量统计表

### 业务说明
支持多粒度（日/周/月）的流量数据存储，用于报表生成和趋势分析。

### 字段详解

| 字段名 | 类型 | 约束 | 说明 | 业务规则 |
|-------|------|------|------|---------|
| id | BIGINT | PK, AUTO_INCREMENT | 统计记录ID | 系统自动生成 |
| intersection_id | BIGINT | FK, NOT NULL | 路口ID | 关联intersections表 |
| stat_date | DATE | NOT NULL | 统计日期 | 日报取当天，周报取周一，月报取月末 |
| stat_type | ENUM | NOT NULL | 统计粒度 | DAILY-日报，WEEKLY-周报，MONTHLY-月报 |
| vehicle_count | INT | DEFAULT 0 | 车流量 | 统计周期内的车辆数 |
| average_speed | FLOAT(5,2) | NULL | 平均车速 | 单位：km/h |
| peak_flow_time | TIME | NULL | 高峰时段 | 如"08:30:00" |
| congestion_duration | INT | DEFAULT 0 | 拥堵时长 | 单位：分钟 |
| violation_count | INT | DEFAULT 0 | 违章次数 | 统计周期内的违章数 |
| recorded_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 数据生成时间 | 报表生成时间 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 创建时间 | 自动记录 |
| updated_at | DATETIME | AUTO UPDATE | 更新时间 | 任何修改自动更新 |

### 业务逻辑
- **唯一性约束**: 同一路口、同一天、同一粒度只能有一条记录
- **数据聚合**: 周报从日报聚合，月报从日报聚合
- **报表生成**: 通过存储过程自动生成周报/月报

### 重要索引
- `uk_intersection_date_type` - 唯一约束
- `idx_intersection_type_date` - 报表查询优化

---

## 6. audit_logs - 审计日志表

### 业务说明
记录系统关键操作，用于追溯权限变更、违章处理、申诉审核等业务操作。

### 字段详解

| 字段名 | 类型 | 约束 | 说明 | 业务规则 |
|-------|------|------|------|---------|
| id | BIGINT | PK, AUTO_INCREMENT | 日志ID | 系统自动生成 |
| operator_id | BIGINT | FK, NULL | 操作人ID | 关联users表，NULL表示系统操作 |
| operation_type | VARCHAR(50) | NOT NULL | 操作类型 | 如SUSPEND_USER, CONFIRM_VIOLATION |
| target_type | VARCHAR(50) | NOT NULL | 目标类型 | USER, VIOLATION, APPEAL等 |
| target_id | BIGINT | NOT NULL | 目标ID | 被操作对象的ID |
| operation_details | JSON | NULL | 操作详情 | JSON格式存储操作前后的数据对比 |
| ip_address | VARCHAR(45) | NULL | 操作IP | 支持IPv4/IPv6 |
| user_agent | VARCHAR(255) | NULL | 用户代理 | 浏览器信息 |
| created_at | DATETIME | DEFAULT CURRENT_TIMESTAMP | 操作时间 | 自动记录 |

### 业务逻辑
- **自动记录**: 通过触发器自动记录关键操作
- **安全审计**: 支持操作回溯和异常分析
- **JSON详情**: 灵活存储操作前后的数据对比

### 常见操作类型
- `CREATE_USER` - 创建用户
- `SUSPEND_USER` - 停用用户
- `CONFIRM_VIOLATION` - 确认违章
- `APPROVE_APPEAL` - 批准申诉
- `REJECT_APPEAL` - 驳回申诉

---

## 🔧 触发器和存储过程

### 触发器列表
1. **trg_violation_status_update** - 违章状态更新时自动记录审计日志
2. **trg_appeal_status_update** - 申诉状态更新时自动同步违章记录
3. **trg_user_status_update** - 用户状态变更时自动记录审计日志

### 存储过程列表
1. **sp_generate_daily_stats(p_stat_date)** - 生成日报
2. **sp_generate_weekly_stats(p_week_start_date)** - 生成周报
3. **sp_generate_monthly_stats(p_year, p_month)** - 生成月报
4. **sp_get_violation_stats(p_intersection_id, p_start_date, p_end_date)** - 查询违章统计

### 函数列表
1. **fn_calculate_penalty(p_violation_type)** - 计算违章罚款金额

---

## 数据量预估

### 初期（1-3个月）
- 用户: 10-20人
- 路口: 10-50个
- 违章记录: 1000-5000条/月
- 申诉记录: 50-200条/月
- 流量统计: 1500条/月（日报30×路口数）

### 成长期（6-12个月）
- 用户: 50-100人
- 路口: 100-300个
- 违章记录: 1万-5万条/月
- 申诉记录: 500-2000条/月
- 流量统计: 9000条/月

### 性能优化建议
- 违章记录超过100万条时，考虑按月分表
- 审计日志超过500万条时，考虑归档历史数据
- 流量统计可按年分表，提高查询效率
