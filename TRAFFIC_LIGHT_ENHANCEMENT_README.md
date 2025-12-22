# 🚦 红绿灯增强功能验证指南

## 🎯 更新内容

### 1. 红绿灯状态扩展
- ✅ 新增 `STRAIGHT_GREEN` (直行绿灯)
- ✅ 新增 `TURN_GREEN` (转弯绿灯)
- ✅ 保持向后兼容 `GREEN` 状态

### 2. 数据库结构更新
- ✅ 添加 `straight_green_duration` 字段
- ✅ 添加 `turn_green_duration` 字段
- ✅ 更新 `current_phase` 枚举类型
- ✅ 创建数据库迁移脚本 `06-signal-upgrade.sql`

### 3. 违章验证逻辑优化
- ✅ **关键改进：** 转弯违章只有在转弯绿灯时才合法
- ✅ 直行绿灯、红灯、黄灯时转弯均构成违章
- ✅ 其他违章类型逻辑保持不变

## 🧪 功能验证步骤

### 第一步：启动服务并执行数据库迁移
```bash
# 如果使用Docker，重新构建并启动
docker-compose down
docker-compose up -d

# 或手动执行SQL迁移脚本
# mysql -u root -p traffic_management < mysql/init/06-signal-upgrade.sql
```

### 第二步：验证新的红绿灯状态
```http
# 1. 设置直行绿灯
POST http://localhost:8081/api/violation-detection/simulate-light
Content-Type: application/x-www-form-urlencoded
intersectionId=1&lightState=STRAIGHT_GREEN&duration=30

# 2. 直行绿灯时转弯（应该构成违章）
POST http://localhost:8081/api/violation-detection/detect-frame
Content-Type: application/json
{
  "intersectionId": 1,
  "plateNumber": "测试001",
  "violationType": "违法转弯",
  "imageUrl": "http://example.com/test.jpg",
  "confidence": 0.95
}
# 预期结果：成功记录违章

# 3. 设置转弯绿灯
POST http://localhost:8081/api/violation-detection/simulate-light
intersectionId=1&lightState=TURN_GREEN&duration=30

# 4. 转弯绿灯时转弯（不应该构成违章）
POST http://localhost:8081/api/violation-detection/detect-frame
# 预期结果：提示"当前红绿灯状态下不构成违章"
```

### 第三步：验证API响应
```http
# 查询红绿灯状态
GET http://localhost:8081/api/violation-detection/light-state/1
# 预期返回：STRAIGHT_GREEN 或 TURN_GREEN

# 查询信号灯配置
GET http://localhost:8081/api/signal-configs
# 预期包含：straightGreenDuration、turnGreenDuration字段
```

## 🎯 核心改进点

### 转弯违章判定逻辑
**之前：** 绿灯时转弯合法，红灯/黄灯时转弯违法
```java
// 旧逻辑
return trafficLightStateService.isRedLight() || trafficLightStateService.isYellowLight();
```

**现在：** 只有转弯绿灯时转弯才合法
```java
// 新逻辑  
var lightState = trafficLightStateService.getCurrentLightState(intersectionId, violationTime);
return lightState != TrafficLightStateService.LightState.TURN_GREEN;
```

### 信号灯周期计算
**信号周期：** 红灯 → 直行绿灯 → 转弯绿灯 → 黄灯

## 📋 测试用例文件
- `traffic-light-enhanced-tests.http` - 完整的增强功能测试用例
- `mysql/init/06-signal-upgrade.sql` - 数据库升级脚本

## ⚠️ 注意事项

1. **数据库迁移：** 首次部署需要执行 `06-signal-upgrade.sql`
2. **向后兼容：** 保持对现有API的兼容性
3. **测试验证：** 建议使用测试用例验证所有场景

## 🚀 生产部署建议

1. 在测试环境验证所有功能
2. 备份生产数据库
3. 执行数据库迁移脚本
4. 重启应用服务
5. 验证关键功能正常运行