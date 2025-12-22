# 🚦 多路口四方向交通信号灯控制系统架构

## 📋 系统概述

本系统支持**多个交通路口**，每个路口**四个方向**（东、南、西、北），每个方向支持**三种转弯类型**（直行、左转、右转）的独立信号灯控制，实现精确的交通违法检测。

## 🎯 核心特性

### 1. 多层次架构
```
路口 (Intersection)
├── 东向 (EAST Direction)
│   ├── 直行信号灯 (Straight)
│   ├── 左转信号灯 (Left Turn) 
│   └── 右转信号灯 (Right Turn)
├── 南向 (SOUTH Direction)
│   ├── 直行信号灯 (Straight)
│   ├── 左转信号灯 (Left Turn)
│   └── 右转信号灯 (Right Turn)
├── 西向 (WEST Direction)
└── 北向 (NORTH Direction)
```

### 2. 精确违章判定
- **闯红灯**：只有对应方向对应转弯类型的信号灯为红灯时才构成违章
- **违法转弯**：只有对应转弯类型的信号灯为绿灯时转弯才合法
- **逆行/跨实线**：不受红绿灯状态影响，始终构成违章

## 🗄️ 数据库设计

### 核心表结构

#### 1. `intersection_directions` - 路口方向配置表
```sql
-- 每个路口的四个方向，每个方向独立的信号灯配置
CREATE TABLE intersection_directions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intersection_id BIGINT NOT NULL,           -- 路口ID
    direction ENUM('EAST','SOUTH','WEST','NORTH'), -- 方向
    
    -- 直行信号灯配置
    straight_red_duration INT,    -- 直行红灯时长
    straight_yellow_duration INT, -- 直行黄灯时长  
    straight_green_duration INT,  -- 直行绿灯时长
    
    -- 左转信号灯配置
    left_turn_red_duration INT,   -- 左转红灯时长
    left_turn_yellow_duration INT,-- 左转黄灯时长
    left_turn_green_duration INT, -- 左转绿灯时长
    
    -- 右转信号灯配置  
    right_turn_red_duration INT,  -- 右转红灯时长
    right_turn_yellow_duration INT,-- 右转黄灯时长
    right_turn_green_duration INT -- 右转绿灯时长
);
```

#### 2. `traffic_phases` - 交通相位配置表
```sql  
-- 定义路口整体的交通相位，协调各方向信号灯时序
CREATE TABLE traffic_phases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intersection_id BIGINT NOT NULL,
    phase_sequence INT NOT NULL,  -- 相位序号
    
    -- 各方向各行为的参与标识
    east_straight BOOLEAN,   -- 东向直行是否参与此相位
    east_left_turn BOOLEAN,  -- 东向左转是否参与此相位
    south_straight BOOLEAN,  -- 南向直行是否参与此相位
    -- ... 其他方向配置
    
    green_duration INT,      -- 该相位绿灯时长
    yellow_duration INT,     -- 该相位黄灯时长  
    all_red_duration INT     -- 全红清空时间
);
```

#### 3. `violations` 表增强
```sql
-- 违章表增加方向和转弯类型字段
ALTER TABLE violations 
ADD COLUMN direction ENUM('EAST','SOUTH','WEST','NORTH'),
ADD COLUMN turn_type ENUM('STRAIGHT','LEFT_TURN','RIGHT_TURN','U_TURN');
```

## 🏗️ Java架构设计

### 实体类层次
```java
// 路口方向实体
@Entity
public class IntersectionDirection {
    private Long intersectionId;
    private Direction direction; // EAST, SOUTH, WEST, NORTH
    
    // 三套独立的信号灯配置
    private Integer straightRedDuration;
    private Integer leftTurnRedDuration; 
    private Integer rightTurnRedDuration;
    // ...
}

// 交通相位实体
@Entity  
public class TrafficPhase {
    private Long intersectionId;
    private Integer phaseSequence;
    
    // 12个布尔字段控制各方向各行为的参与
    private Boolean eastStraight;
    private Boolean eastLeftTurn;
    private Boolean southStraight;
    // ...
}

// 违章实体增强
@Entity
public class Violation {
    private Long intersectionId;
    private Direction direction;     // 违章发生方向
    private TurnType turnType;      // 行驶类型
    private ViolationType violationType; // 违章类型
    // ...
}
```

### 服务层架构
```java
@Service
public class MultiDirectionTrafficLightService {
    
    // 获取指定路口指定方向指定转弯类型的当前信号灯状态
    public LightPhase getCurrentLightState(Long intersectionId, 
                                          Direction direction, 
                                          TurnType turnType, 
                                          LocalDateTime checkTime);
    
    // 验证违章是否构成违法
    public boolean validateViolationWithMultiDirectionLight(Long intersectionId,
                                                            Direction direction,
                                                            TurnType turnType, 
                                                            ViolationType violationType,
                                                            LocalDateTime violationTime);
}
```

## 🎮 API接口设计

### 基础查询接口
```http
# 获取路口所有方向配置
GET /api/multi-direction-traffic/intersections/{id}/directions

# 获取路口所有方向当前状态  
GET /api/multi-direction-traffic/intersections/{id}/status

# 获取特定方向特定转弯类型状态
GET /api/multi-direction-traffic/intersections/{id}/directions/{direction}/turns/{turnType}/status
```

### 控制和测试接口
```http
# 模拟设置信号灯状态
POST /api/multi-direction-traffic/intersections/{id}/simulate
direction=SOUTH&turnType=LEFT_TURN&lightPhase=GREEN&duration=30

# 违章验证测试
POST /api/multi-direction-traffic/intersections/{id}/validate-violation
{
  "direction": "SOUTH",
  "turnType": "LEFT_TURN", 
  "violationType": "ILLEGAL_TURN"
}
```

### 违章检测接口增强
```http
# 违章检测（增加方向和转弯类型参数）
POST /api/violation-detection/detect-frame
{
  "intersectionId": 1,
  "direction": "SOUTH",      # 新增：违章发生方向
  "turnType": "LEFT_TURN",   # 新增：车辆行驶类型
  "plateNumber": "沪A12345",
  "violationType": "违法转弯",
  "imageUrl": "...",
  "confidence": 0.95
}
```

## 🔄 信号控制逻辑

### 标准四相位配置
```
相位一：南北直行绿灯  (40秒绿灯 + 3秒黄灯 + 2秒全红)
相位二：南北左转绿灯  (20秒绿灯 + 3秒黄灯 + 2秒全红)  
相位三：东西直行绿灯  (35秒绿灯 + 3秒黄灯 + 2秒全红)
相位四：东西左转绿灯  (18秒绿灯 + 3秒黄灯 + 2秒全红)
```

### 违章判定矩阵

| 方向 | 转弯类型 | 信号灯状态   | 行为 | 判定结果     |
| ---- | -------- | ------------ | ---- | ------------ |
| 南向 | 直行     | 南向直行绿灯 | 直行 | ✅ 合法       |
| 南向 | 直行     | 南向直行红灯 | 直行 | ❌ 闯红灯违章 |
| 南向 | 左转     | 南向左转绿灯 | 左转 | ✅ 合法       |
| 南向 | 左转     | 南向左转红灯 | 左转 | ❌ 违法转弯   |
| 东向 | 右转     | 东向右转绿灯 | 右转 | ✅ 合法       |
| 任意 | 任意     | 任意         | 逆行 | ❌ 始终违章   |

## 🚀 部署和初始化

### 1. 数据库初始化
```bash
# 执行多方向升级脚本
mysql -u root -p traffic_management < mysql/init/07-multi-direction-upgrade.sql
```

### 2. 自动初始化数据
- ✅ 为每个现有路口自动创建四个方向配置
- ✅ 为每个路口创建标准四相位配置
- ✅ 设置合理的默认信号灯时长

### 3. 测试验证
```bash
# 使用完整测试用例验证功能
# multi-direction-traffic-tests.http
```

## 📊 系统优势

### 1. **精确控制**
- 每个路口四个方向独立配置
- 每个方向三种转弯类型独立控制
- 支持复杂的相位配置

### 2. **灵活扩展**  
- 支持不同路口不同配置方案
- 支持动态调整信号灯时长
- 支持智能化相位优化

### 3. **准确判定**
- 精确到方向和转弯类型的违章判定
- 避免误判和漏判
- 支持复杂交通场景

### 4. **高性能**
- Redis缓存提升查询性能
- 数据库索引优化
- 支持高并发违章检测

## 🎯 使用场景

### 典型应用
1. **城市交通路口监控**：多个路口的统一管理
2. **智能信号灯控制**：根据流量动态调整相位
3. **违章行为检测**：精确识别各类交通违法行为
4. **交通数据分析**：基于方向和转弯类型的统计分析

### 扩展可能
1. **AI智能调配**：基于车流量自动优化相位
2. **绿波带控制**：协调多个路口的信号时序  
3. **应急响应**：特殊情况下的信号灯紧急控制
4. **移动端监控**：交警移动端实时监控和控制

---

**这就是完整的多路口四方向交通信号灯控制系统！** 🎉

系统支持精确到**路口-方向-转弯类型**三个维度的信号控制，实现了真正智能化的交通管理。