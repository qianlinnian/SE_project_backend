# TrafficMind 交通智脑 - 快速启动指南

## 快速启动

### 1. 环境准备
```powershell
# 确认Docker版本
docker --version
# 应显示: Docker version 28.5.2 或更高版本

# 确认Docker Compose可用
docker-compose --version
```

### 2. 启动数据库环境
```powershell
# 在项目根目录执行
docker-compose up -d

# 查看容器状态
docker-compose ps

# 查看MySQL初始化日志（等待初始化完成）
docker-compose logs -f traffic-db
```

### 3. 访问数据库
- **Adminer管理界面**: http://localhost:8080
  - 服务器: `traffic-db`
  - 用户名: `root`
  - 密码: `TrafficMind@2024`
  - 数据库: `traffic_mind`

- **VS Code数据库插件连接**:
  - Host: `localhost`
  - Port: `3306`
  - User: `root`
  - Password: `TrafficMind@2024`
  - Database: `traffic_mind`

### 4. 验证数据
```sql
-- 查看所有表
SHOW TABLES;

-- 验证测试用户
SELECT username, full_name, role FROM users;

-- 验证路口信息
SELECT name, current_status FROM intersections;

-- 统计违章记录
SELECT status, COUNT(*) as count FROM violations GROUP BY status;
```

### 5. 停止环境
```powershell
# 停止但保留数据
docker-compose stop

# 停止并删除容器（数据保留在mysql/data目录）
docker-compose down

# 停止并删除所有数据（谨慎使用）
docker-compose down -v
```

## 测试数据说明

### 测试账户
| 用户名 | 密码 | 角色 | 状态 |
|--------|------|------|------|
| admin | password123 | 管理员 | 正常 |
| police001 | password123 | 交警(张三) | 正常 |
| police002 | password123 | 交警(李四) | 正常 |
| police003 | password123 | 交警(王五) | 正常 |
| police004 | password123 | 交警(赵六) | 已停用 |

### 数据统计
- 8个测试路口
- 500条违章记录（300已确认 + 150待审核 + 50已驳回）
- 20条申诉记录
- 90天流量数据（支持日/周/月报表）

## 常用SQL操作

### 查询待审核违章
```sql
SELECT v.id, i.name as intersection_name, v.plate_number, v.violation_type, v.occurred_at
FROM violations v
JOIN intersections i ON v.intersection_id = i.id
WHERE v.status = 'PENDING'
ORDER BY v.occurred_at DESC;
```

### 生成报表
```sql
-- 生成今天的日报
CALL sp_generate_daily_stats(CURDATE());

-- 生成本周的周报
CALL sp_generate_weekly_stats('2024-12-09');

-- 生成本月的月报
CALL sp_generate_monthly_stats(2024, 12);
```

### 查询路口违章统计
```sql
CALL sp_get_violation_stats(1, '2024-11-01', '2024-11-30');
```

## 注意事项

1. **首次启动**: MySQL初始化需要30-60秒，请耐心等待
2. **数据持久化**: 数据存储在 `mysql/data/` 目录，删除容器不会丢失数据
3. **端口冲突**: 确保3306和8080端口未被占用
4. **密码安全**: 生产环境请修改 `.env` 中的密码

## 🐛 故障排查

### 容器启动失败
```powershell
# 查看错误日志
docker-compose logs traffic-db

# 重新构建
docker-compose down
docker-compose up -d --force-recreate
```

### 无法连接数据库
```powershell
# 检查容器运行状态
docker-compose ps

# 进入MySQL容器
docker exec -it traffic_db mysql -uroot -p
```

### 重置数据库
```powershell
# 停止容器
docker-compose down

# 删除数据目录
Remove-Item -Recurse -Force mysql/data

# 重新启动（会自动初始化）
docker-compose up -d

```
SE_project_backend
├─ ai_detection
│  ├─ api
│  │  ├─ ai_realtime_service.py
│  │  ├─ backend_api_client.py
│  │  └─ detection_api.py
│  ├─ core
│  │  ├─ image_violation_detector.py
│  │  ├─ vehicle_tracker.py
│  │  └─ violation_detector.py
│  ├─ data
│  ├─ main_pipeline.py
│  ├─ PYTHON_FILES.md
│  ├─ README.md
│  ├─ requirements.txt
│  ├─ scripts
│  │  ├─ main_pipeline_manual.py
│  │  ├─ manual_signal_controller.py
│  │  ├─ test_backend_integration.py
│  │  ├─ test_flask_api.py
│  │  ├─ test_image.py
│  │  ├─ test_realtime_service.py
│  │  ├─ test_yolo_simple.py
│  │  └─ visualize_detection.py
│  └─ tools
│     ├─ roi_labeler.py
│     ├─ roi_visualizer.py
│     ├─ signal_adapter.py
│     └─ video_rotator.py
├─ api-tests.http
├─ com
│  └─ traffic
│     └─ management
│        └─ util
│           └─ PasswordTest.java
├─ docker-compose.yml
├─ docs
│  ├─ api_traffic_violation_en.md
│  ├─ diagrams
│  │  ├─ traffic-monitor-architecture.mmd
│  │  ├─ traffic-monitor-class.mmd
│  │  ├─ traffic-monitor-sequence.mmd
│  │  ├─ violation-architecture.mmd
│  │  └─ violation-class.mmd
│  ├─ ER_diagram.md
│  ├─ schema_notes.md
│  └─ 智能交通管理系统 - 接口与功能说明文档.pdf
├─ minio
├─ mysql
│  └─ init
│     ├─ 01-schema.sql
│     ├─ 02-indices.sql
│     ├─ 03-seed-data.sql
│     ├─ 04-functions.sql
│     ├─ 05-signal-tables.sql
│     ├─ 06-signal-upgrade.sql
│     ├─ 07-multi-direction-upgrade.sql
│     ├─ 08-video-analysis-tasks.sql
│     └─ 09-add-violation-types.sql
├─ pom.xml
├─ postman_collection.json
├─ README.md
├─ redis
└─ src
   └─ main
      ├─ java
      │  └─ com
      │     └─ traffic
      │        └─ management
      │           ├─ config
      │           │  ├─ AppConfig.java
      │           │  ├─ AsyncConfig.java
      │           │  ├─ CorsConfig.java
      │           │  ├─ JacksonConfig.java
      │           │  ├─ MinioConfig.java
      │           │  ├─ RedisConfig.java
      │           │  ├─ SecurityConfig.java
      │           │  └─ WebSocketConfig.java
      │           ├─ controller
      │           │  ├─ AdminController.java
      │           │  ├─ AIIntegrationController.java
      │           │  ├─ AuthController.java
      │           │  ├─ FileController.java
      │           │  ├─ HealthController.java
      │           │  ├─ ImageDetectionController.java
      │           │  ├─ MultiDirectionTrafficController.java
      │           │  ├─ RedisTestController.java
      │           │  ├─ SignalController.java
      │           │  ├─ TestController.java
      │           │  ├─ TrafficMonitorController.java
      │           │  ├─ VideoDetectionController.java.temp
      │           │  ├─ ViolationController.java
      │           │  └─ ViolationDetectionController.java
      │           ├─ dto
      │           │  ├─ request
      │           │  │  ├─ LoginRequest.java
      │           │  │  ├─ PoliceCreateRequest.java
      │           │  │  ├─ SignalAdjustRequest.java
      │           │  │  └─ UserStatusUpdateRequest.java
      │           │  └─ response
      │           │     ├─ ApiResponse.java
      │           │     ├─ LoginResponse.java
      │           │     ├─ PageResponse.java
      │           │     └─ SignalConfigResponse.java
      │           ├─ entity
      │           │  ├─ AiDetectionResult.java
      │           │  ├─ Intersection.java
      │           │  ├─ IntersectionDirection.java
      │           │  ├─ SignalConfig.java
      │           │  ├─ SignalLog.java
      │           │  ├─ TrafficPhase.java
      │           │  ├─ User.java
      │           │  ├─ VideoAnalysisTask.java
      │           │  └─ Violation.java
      │           ├─ exception
      │           │  ├─ BusinessException.java
      │           │  ├─ ErrorCode.java
      │           │  └─ GlobalExceptionHandler.java
      │           ├─ handler
      │           │  └─ AlertWebSocketHandler.java
      │           ├─ repository
      │           │  ├─ AiDetectionResultRepository.java
      │           │  ├─ IntersectionDirectionRepository.java
      │           │  ├─ IntersectionRepository.java
      │           │  ├─ SignalConfigRepository.java
      │           │  ├─ SignalLogRepository.java
      │           │  ├─ TrafficPhaseRepository.java
      │           │  ├─ UserRepository.java
      │           │  ├─ VideoAnalysisTaskRepository.java
      │           │  └─ ViolationRepository.java
      │           ├─ security
      │           │  ├─ JwtAuthenticationFilter.java
      │           │  └─ JwtTokenProvider.java
      │           ├─ service
      │           │  ├─ AIIntegrationService.java
      │           │  ├─ AuthService.java
      │           │  ├─ MultiDirectionTrafficLightService.java
      │           │  ├─ NotificationService.java
      │           │  ├─ RedisService.java
      │           │  ├─ SignalService.java
      │           │  ├─ TaskScheduleService.java
      │           │  ├─ TaskStatusService.java
      │           │  ├─ TrafficLightStateService.java
      │           │  ├─ UserInitializationService.java
      │           │  ├─ UserService.java
      │           │  ├─ VideoDetectionService.java
      │           │  ├─ VideoFrameAnalysisService.java
      │           │  ├─ VideoTaskStatusService.java
      │           │  └─ ViolationService.java
      │           ├─ TrafficManagementApplication.java
      │           └─ util
      │              ├─ PasswordGeneratorUtil.java
      │              └─ PasswordVerifyUtil.java
      └─ resources
         ├─ application.properties
         └─ logback-spring.xml

```