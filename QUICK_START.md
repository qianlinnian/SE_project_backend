# TrafficMind 快速启动指南

## 🚀 5分钟快速部署

### 前置要求
- ✅ Docker 20.10+
- ✅ Docker Compose 2.0+
- ✅ 8GB+ 内存
- ✅ 50GB+ 磁盘空间

### 一键启动

```bash
# 1. 进入项目目录
cd SE_project_backend

# 2. 启动所有服务（使用启动脚本）
./start.sh

# 或者直接使用 docker-compose
docker-compose up -d
```

### 验证部署

```bash
# 检查服务状态
docker-compose ps

# 应该看到以下服务都在运行：
# - traffic_db        (MySQL)
# - traffic_redis     (Redis)
# - traffic_minio     (MinIO)
# - traffic_backend   (Java后端)
# - traffic_ai        (Python AI)
# - traffic_db_admin  (Adminer)
```

### 访问服务

| 服务 | 地址 | 说明 |
|------|------|------|
| **Java 后端 API** | http://localhost:8081 | Spring Boot 服务 |
| **Python AI 服务** | http://localhost:5000 | AI 检测服务 |
| **数据库管理** | http://localhost:8080 | Adminer Web界面 |
| **MinIO 控制台** | http://localhost:9001 | 对象存储管理 |

### 测试 API

```bash
# 1. 测试 AI 服务健康检查
curl http://localhost:5000/health

# 2. 测试后端登录
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}'

# 3. 获取 JWT Token 后测试其他接口
TOKEN="<your-jwt-token>"
curl http://localhost:8081/api/violations \
  -H "Authorization: Bearer $TOKEN"
```

### 查看日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 只查看特定服务
docker-compose logs -f backend    # Java 后端
docker-compose logs -f ai-service # Python AI
docker-compose logs -f traffic-db # MySQL
```

### 停止服务

```bash
# 停止但保留数据
docker-compose stop

# 停止并删除容器（保留数据卷）
docker-compose down

# 完全清理（包括数据）
docker-compose down -v
```

---

## 🔧 故障排查

### 问题 1: 端口被占用

**错误**: `bind: address already in use`

**解决**:
```bash
# 检查端口占用
netstat -an | grep 8081  # Windows
lsof -i :8081           # Linux/Mac

# 修改端口（编辑 docker-compose.yml）
ports:
  - "8082:8081"  # 改为其他端口
```

### 问题 2: 数据库连接失败

**错误**: `Communications link failure`

**解决**:
```bash
# 1. 等待数据库初始化完成（约30-60秒）
docker logs -f traffic_db

# 2. 检查数据库健康状态
docker exec traffic_db mysqladmin ping -u root -pTrafficMind@2024

# 3. 手动重启后端服务
docker-compose restart backend
```

### 问题 3: AI 服务启动失败

**错误**: `ModuleNotFoundError: No module named 'ultralytics'`

**解决**:
```bash
# 重新构建镜像
docker-compose build --no-cache ai-service
docker-compose up -d ai-service
```

### 问题 4: 文件上传权限问题

**错误**: `Permission denied: '/app/uploads'`

**解决**:
```bash
# 修改本地上传目录权限
mkdir -p uploads/violation uploads/general
chmod -R 777 uploads
```

---

## 📊 默认账号信息

### 应用账号

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| `admin` | `password123` | 管理员 | 系统管理员 |
| `police01` | `police123` | 交警 | 普通交警账号 |
| `ai-detection-service` | `ai_service_2025` | AI服务 | AI服务专用账号 |

### 基础设施账号

| 服务 | 用户名 | 密码 |
|------|--------|------|
| **MySQL** | `root` | `TrafficMind@2024` |
| **MinIO** | `minioadmin` | `minioadmin` |
| **Redis** | - | 无密码 |

**⚠️ 生产环境务必修改所有默认密码！**

---

## 📁 目录结构说明

```
SE_project_backend/
├── ai_detection/          # Python AI 服务
│   ├── Dockerfile         # ✅ AI服务镜像
│   ├── api/              # Flask API
│   ├── core/             # 核心检测逻辑
│   └── data/             # ROI配置等
├── src/                   # Java 后端源码
├── mysql/
│   ├── init/             # 数据库初始化脚本
│   │   └── 10-signal-init-data.sql  # ✅ 信号灯配置
│   └── data/             # 数据持久化目录
├── uploads/              # 文件上传目录
│   ├── violation/        # 违规截图
│   └── general/          # 通用文件
├── logs/                 # 日志目录
│   ├── ai/              # AI服务日志
│   └── backend/         # 后端日志
├── Dockerfile            # ✅ Java后端镜像
├── docker-compose.yml    # ✅ 编排配置
├── .env                  # ✅ 环境变量
├── start.sh             # ✅ 快速启动脚本
└── DEPLOYMENT_GUIDE.md  # 详细部署文档
```

---

## 🎯 下一步

1. **修改默认密码**
   ```bash
   # 编辑 .env 文件
   vim .env
   ```

2. **配置域名和 SSL**
   ```bash
   # 使用 Nginx 反向代理 + Let's Encrypt
   # 参考 DEPLOYMENT_GUIDE.md
   ```

3. **上传测试视频**
   ```bash
   # 放置视频到 ai_detection/data/
   cp your_video.mp4 ai_detection/data/
   ```

4. **测试违规检测**
   ```bash
   # 访问前端（如果已部署）
   # 或使用 Postman 测试 API
   ```

---

## 📞 获取帮助

- 📖 **详细文档**: [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)
- 🏗️ **架构文档**: [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md)
- 🐛 **问题反馈**: 联系开发团队

---

**祝部署顺利！** 🎉

**TrafficMind Team**
冯俊财 | 关镜文 | 路清怡 | 黄弋涵
