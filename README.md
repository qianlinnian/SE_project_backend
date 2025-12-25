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
 
### java
```powershell
cd d:\course_content\SE\seprojects\SE_project_backend
mvn spring-boot:run
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
