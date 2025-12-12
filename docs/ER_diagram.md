# TrafficMind 交通智脑 - 数据库ER图

## 实体关系图（Entity-Relationship Diagram）

```
┌─────────────────────┐
│      users          │
│  (用户账户表)        │
├─────────────────────┤
│ PK: id              │
│     username        │
│     password_hash   │
│     full_name       │
│     role            │
│     police_number   │
│     status          │
└──────────┬──────────┘
           │
           │ 1:N (processed_by)
           │
           ▼
┌─────────────────────┐          1:N          ┌─────────────────────┐
│   intersections     │◄─────────────────────┤    violations       │
│  (交通路口表)        │                       │   (违章记录表)       │
├─────────────────────┤                       ├─────────────────────┤
│ PK: id              │                       │ PK: id              │
│     name            │                       │ FK: intersection_id │
│     latitude        │                       │ FK: processed_by    │
│     longitude       │                       │     plate_number    │
│     device_ip       │                       │     violation_type  │
│     device_id       │                       │     image_url       │
│     current_status  │                       │     ai_confidence   │
│     capacity_level  │                       │     occurred_at     │
└──────────┬──────────┘                       │     status          │
           │                                  │     penalty_amount  │
           │ 1:N                              │     appeal_status   │
           │                                  └──────────┬──────────┘
           │                                             │
           ▼                                             │ 1:1
┌─────────────────────┐                                 │
│   traffic_stats     │                                 ▼
│  (流量统计表)        │                       ┌─────────────────────┐
├─────────────────────┤                       │      appeals        │
│ PK: id              │                       │   (申诉记录表)       │
│ FK: intersection_id │                       ├─────────────────────┤
│     stat_date       │                       │ PK: id              │
│     stat_type       │                       │ FK: violation_id    │
│     vehicle_count   │                       │ FK: reviewed_by     │
│     average_speed   │                       │     appeal_reason   │
│     peak_flow_time  │                       │     status          │
│     congestion_dur  │                       │     result          │
│     violation_count │                       │     original_penalty│
└─────────────────────┘                       │     adjusted_penalty│
                                              └─────────────────────┘
           
           ┌─────────────────────┐
           │    audit_logs       │
           │   (审计日志表)       │
           ├─────────────────────┤
           │ PK: id              │
           │ FK: operator_id     │
           │     operation_type  │
           │     target_type     │
           │     target_id       │
           │     operation_detail│
           │     ip_address      │
           └─────────────────────┘
```

## 核心关系说明

### 1. users ↔ violations (1:N)
- **关系字段**: `violations.processed_by` → `users.id`
- **说明**: 一个交警可以处理多个违章记录
- **业务场景**: 交警审核违章时，记录处理人信息

### 2. intersections ↔ violations (1:N)
- **关系字段**: `violations.intersection_id` → `intersections.id`
- **说明**: 一个路口可以产生多个违章记录
- **业务场景**: 根据路口统计违章热点

### 3. violations ↔ appeals (1:1)
- **关系字段**: `appeals.violation_id` → `violations.id` (UNIQUE)
- **说明**: 一个违章记录最多对应一个申诉记录
- **业务场景**: 驾驶员对违章处罚提出申诉

### 4. intersections ↔ traffic_stats (1:N)
- **关系字段**: `traffic_stats.intersection_id` → `intersections.id`
- **说明**: 一个路口对应多条流量统计数据（日/周/月）
- **业务场景**: 生成多粒度交通报表

### 5. users ↔ appeals (1:N)
- **关系字段**: `appeals.reviewed_by` → `users.id`
- **说明**: 一个交警/管理员可以审核多个申诉
- **业务场景**: 申诉审核流程

### 6. users ↔ audit_logs (1:N)
- **关系字段**: `audit_logs.operator_id` → `users.id`
- **说明**: 一个用户可以产生多条审计日志
- **业务场景**: 追溯用户操作历史

## 数据流转图

```
【AI识别违章】
     ↓
【创建违章记录】(status: PENDING)
     ↓
【交警审核】
     ├─ CONFIRMED → 【生成罚款单】 → 【可申诉】
     │                                   ↓
     │                            【提交申诉】(status: SUBMITTED)
     │                                   ↓
     │                            【交警/管理员审核】
     │                                   ↓
     │                            ┌─────┴─────┐
     │                       APPROVED      REJECTED
     │                            │              │
     │                     【罚款调整/免除】  【维持原判】
     │
     └─ REJECTED → 【流程结束】
```

## 索引策略

### 高频查询索引
- `violations.status` - 按状态过滤待审核违章
- `violations.intersection_id + occurred_at` - 按路口和时间查询
- `violations.plate_number` - 按车牌号查询历史违章
- `traffic_stats.intersection_id + stat_type + stat_date` - 报表生成
- `appeals.status` - 按申诉状态过滤

### 复合索引优化
- `idx_intersection_status` - 路口+状态联合查询
- `idx_intersection_type_date` - 报表数据快速聚合
- `idx_operator_created` - 审计日志按操作人+时间查询

## 数据完整性约束

| 约束类型 | 应用场景 | 说明 |
|---------|---------|------|
| PRIMARY KEY | 所有表的id字段 | 确保记录唯一性 |
| FOREIGN KEY | 跨表关联 | 维护引用完整性 |
| UNIQUE KEY | username, police_number, violation_id | 防止重复数据 |
| NOT NULL | 关键业务字段 | 防止空值 |
| DEFAULT | 状态字段 | 设置默认值 |
| ON DELETE CASCADE | appeals.violation_id | 级联删除 |
| ON DELETE SET NULL | processed_by, reviewed_by | 保留历史记录 |

## 扩展性设计

### 1. 支持未来扩展的字段
- `violations.ai_confidence` - AI识别置信度
- `audit_logs.operation_details` - JSON格式，灵活存储操作详情
- `intersections.capacity_level` - 路口容量模型

### 2. 多粒度报表支持
- `traffic_stats.stat_type` - DAILY/WEEKLY/MONTHLY
- 支持从日报聚合周报、月报

### 3. 审计追溯
- 所有关键操作自动记录到 `audit_logs`
- 支持操作回溯和安全审计
