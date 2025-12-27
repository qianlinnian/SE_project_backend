# 信号灯配置修改说明

## 📝 修改概述

**修改日期**: 2025-12-27
**修改人**: 冯俊财
**修改原因**: 简化系统，移除右转信号灯逻辑，统一信号周期为60秒

---

## 🎯 修改内容

### 1. 删除右转信号灯逻辑

**原因**:
- 大多数路口右转不需要单独信号灯
- 简化系统复杂度
- 降低数据存储和计算开销

**影响范围**:
- 数据库配置
- Java 后端计算逻辑
- API 返回数据（右转字段仍保留但固定为RED）

### 2. 统一信号周期为60秒

**原因**:
- 便于计算和理解
- 符合常见交通信号周期标准
- 简化配置管理

**新配置**:
- 所有方向总周期: 60秒
- 南北向: 红30s → 绿27s → 黄3s
- 东西向: 红30s → 绿27s → 黄3s
- 左转: 红45s → 绿12s → 黄3s

---

## 🔧 修改的文件

### SQL 初始化脚本

**文件**: `mysql/init/10-signal-init-data.sql`

**修改内容**:
```sql
-- 右转配置全部设置为 0
right_turn_red_duration = 0,
right_turn_yellow_duration = 0,
right_turn_green_duration = 0,

-- 直行和左转配置调整为60秒周期
30, 3, 27,  -- 直行：红30 黄3 绿27 (总60秒)
45, 3, 12,  -- 左转：红45 黄3 绿12 (总60秒)
```

**四个方向配置**:
| 方向 | 直行周期 | 左转周期 | 右转 | 初始状态 |
|------|---------|---------|------|---------|
| 北向(NORTH) | 红30-绿27-黄3 | 红45-绿12-黄3 | 已废弃 | 红灯 |
| 南向(SOUTH) | 红30-绿27-黄3 | 红45-绿12-黄3 | 已废弃 | 红灯 |
| 东向(EAST) | 红30-绿27-黄3 | 红45-绿12-黄3 | 已废弃 | 绿灯 |
| 西向(WEST) | 红30-绿27-黄3 | 红45-绿12-黄3 | 已废弃 | 绿灯 |

---

### Java 服务类

**文件**: `src/main/java/com/traffic/management/service/MultiDirectionTrafficLightService.java`

**修改位置 1**: `calculateDirectionLightState()` 方法（第172-174行）
```java
// 原代码: 计算右转信号灯（30行代码）
// 新代码:
// 右转信号灯已废弃，固定返回RED和0剩余时间
IntersectionDirection.LightPhase rightTurnPhase = IntersectionDirection.LightPhase.RED;
int rightTurnRemaining = 0;
```

**修改位置 2**: `getCurrentLightState()` 方法（第106-108行）
```java
case RIGHT_TURN:
    // 右转信号灯已废弃，固定返回RED（表示禁止右转或需要遵守直行信号）
    return IntersectionDirection.LightPhase.RED;
```

**修改位置 3**: `createSimulatedState()` 方法（第281-282, 292-294, 298行）
```java
// 右转信号灯已废弃，固定为RED
IntersectionDirection.LightPhase rightTurnPhase = IntersectionDirection.LightPhase.RED;

case RIGHT_TURN:
    // 右转信号灯已废弃，不设置
    break;

return new DirectionLightState(straightPhase, leftTurnPhase, rightTurnPhase,
        durationSeconds, durationSeconds, 0);  // 右转剩余时间固定为0
```

---

## 📊 API 返回数据变化

### 修改前

```json
{
  "NORTH": {
    "straightPhase": "RED",
    "leftTurnPhase": "RED",
    "rightTurnPhase": "GREEN",  // 动态计算
    "straightRemaining": 40,
    "leftTurnRemaining": 50,
    "rightTurnRemaining": 25    // 动态计算
  }
}
```

### 修改后

```json
{
  "NORTH": {
    "straightPhase": "RED",
    "leftTurnPhase": "RED",
    "rightTurnPhase": "RED",    // 固定为RED
    "straightRemaining": 30,     // 新周期
    "leftTurnRemaining": 45,     // 新周期
    "rightTurnRemaining": 0      // 固定为0
  }
}
```

**注意**: API 结构未改变，保持向后兼容，但 `rightTurnPhase` 和 `rightTurnRemaining` 字段现在固定值。

---

## ⚠️ 向后兼容性

### 数据库字段

**保留但不使用**:
- `right_turn_red_duration`
- `right_turn_yellow_duration`
- `right_turn_green_duration`
- `current_right_turn_phase`
- `right_turn_phase_remaining`

这些字段在数据库中保留（值为0或RED），以避免破坏现有数据结构。

### API 接口

所有 API 接口保持不变：
- ✅ `GET /api/multi-direction-traffic/intersections/{id}/status`
- ✅ `POST /api/multi-direction-traffic/intersections/{id}/simulate`

返回数据中仍包含 `rightTurnPhase` 和 `rightTurnRemaining` 字段，但值固定。

### 前端影响

如果前端代码使用了右转信号灯数据：
```javascript
// 修改前
if (data.rightTurnPhase === 'GREEN') {
    // 允许右转
}

// 修改后
// rightTurnPhase 永远是 'RED'，需要改为检查直行信号
if (data.straightPhase === 'GREEN') {
    // 右转遵循直行信号
}
```

---

## 🚀 部署说明

### 新部署

直接使用更新后的配置即可：
```bash
docker-compose up -d
```

数据库初始化时会自动执行 `10-signal-init-data.sql`，创建60秒周期的配置。

### 已有部署更新

如果数据库已有数据，执行更新脚本：

```sql
USE traffic_mind;

-- 更新所有方向为60秒周期，移除右转
UPDATE intersection_directions
SET
    -- 直行: 60秒周期
    straight_red_duration = 30,
    straight_yellow_duration = 3,
    straight_green_duration = 27,

    -- 左转: 60秒周期
    left_turn_red_duration = 45,
    left_turn_yellow_duration = 3,
    left_turn_green_duration = 12,

    -- 右转: 废弃
    right_turn_red_duration = 0,
    right_turn_yellow_duration = 0,
    right_turn_green_duration = 0,
    current_right_turn_phase = 'RED',
    right_turn_phase_remaining = 0,

    updated_at = NOW()
WHERE intersection_id = 1;

-- 验证更新
SELECT
    direction,
    CONCAT('直行:', straight_red_duration, '+', straight_green_duration, '+', straight_yellow_duration, '=',
           straight_red_duration + straight_green_duration + straight_yellow_duration) AS straight_cycle,
    CONCAT('左转:', left_turn_red_duration, '+', left_turn_green_duration, '+', left_turn_yellow_duration, '=',
           left_turn_red_duration + left_turn_green_duration + left_turn_yellow_duration) AS left_turn_cycle
FROM intersection_directions
WHERE intersection_id = 1;
```

### 重启服务

```bash
# 重启 Java 后端以加载新逻辑
docker-compose restart backend

# 清除 Redis 缓存（可选）
docker exec traffic_redis redis-cli FLUSHDB
```

---

## ✅ 验证测试

### 测试步骤

1. **检查数据库配置**
   ```sql
   SELECT * FROM intersection_directions WHERE intersection_id = 1;
   ```

   期望结果：所有方向的右转时长为0

2. **测试 API**
   ```bash
   curl http://localhost:8081/api/multi-direction-traffic/intersections/1/status
   ```

   期望结果：
   ```json
   {
     "NORTH": {
       "rightTurnPhase": "RED",
       "rightTurnRemaining": 0
     },
     ...
   }
   ```

3. **检查信号周期**

   观察60秒内信号灯变化：
   - 0-30秒: 直行红灯
   - 30-57秒: 直行绿灯
   - 57-60秒: 直行黄灯
   - 循环

---

## 📌 注意事项

1. **违规检测影响**
   - 系统不再检测"右转闯红灯"
   - 如需右转违规检测，应改为检查"直行信号"

2. **数据统计影响**
   - 历史数据中的右转违规记录仍然有效
   - 新数据不会再有 `RIGHT_TURN` 类型违规

3. **前端UI建议**
   - 隐藏或灰化右转信号灯显示
   - 或显示"遵循直行信号"提示

---

## 🔄 回滚方案

如需恢复右转信号灯功能：

1. **恢复 SQL 配置**
   ```sql
   UPDATE intersection_directions
   SET
       right_turn_red_duration = 5,
       right_turn_yellow_duration = 3,
       right_turn_green_duration = 60
   WHERE intersection_id = 1;
   ```

2. **回滚 Java 代码**

   使用 Git 恢复文件：
   ```bash
   git checkout HEAD~1 -- src/main/java/com/traffic/management/service/MultiDirectionTrafficLightService.java
   ```

3. **重新构建部署**
   ```bash
   docker-compose build backend
   docker-compose restart backend
   ```

---

## 📞 联系信息

如有问题，请联系：
- **开发团队**: Coders
- **负责人**: 冯俊财

---

**文档版本**: 1.0.0
**最后更新**: 2025-12-27
