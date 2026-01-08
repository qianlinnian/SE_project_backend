# TrafficMind 交通智脑

> 基于 AI 的智能交通违规检测与监控系统

**团队**: Coders (冯俊财、关镜文、路清怡、黄弋涵)

---

## 🚀 快速开始

### 1. 首次部署（服务器）
```bash
# 克隆仓库
git clone <repository-url>
cd SE_project_backend

# 启动服务
docker-compose up -d

# 查看服务状态
docker-compose ps
```

> 详细部署步骤请查看：**[DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)** ⭐

### 2. 更新部署
```bash
# 一键更新脚本
bash update.sh

# 查看日志
docker-compose logs -f
```

### 3. 本地开发
```bash
# 启动数据库
docker-compose up -d traffic-db redis minio

# 启动后端
mvn spring-boot:run

# 启动 AI 服务
cd ai_detection
python api/ai_realtime_service.py
```

---

## 📚 核心功能

### 🤖 AI 车辆检测
- **YOLOv8** 实时车辆检测
- **DeepSORT** 多目标追踪
- 支持视频流和单张图片检测

### 🚦 智能违规检测
- ✅ 闯红灯检测 (Red Light Running)
- ✅ 逆行检测 (Wrong-Way Driving)
- ✅ 跨实线变道 (Cross Solid Line)
- ✅ 违法转弯 (Illegal Turn)

### 📡 实时数据推送
- WebSocket 实时视频流
- 实时信号灯同步
- 违规事件即时告警

### 🔐 后端管理系统
- 用户认证与授权 (JWT)
- 违规记录管理
- 文件上传与存储 (MinIO)
- 数据统计与报表

---

## 🏗️ 技术架构

### 前端
- Vue 3 + TypeScript
- Element Plus UI
- WebSocket 实时通信

### 后端
- Java 17 + Spring Boot 3.4.5
- MySQL 8.0 + Redis
- MinIO 对象存储

### AI 服务
- Python 3.10
- YOLOv8 (Ultralytics)
- Flask + Flask-SocketIO
- OpenCV

### 部署
- Docker + Docker Compose
- Nginx 反向代理

> 详细架构说明请查看：**[SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md)**

---

## 📂 项目结构

```
SE_project_backend/
├── ai_detection/              # AI 检测服务（Python）
│   ├── api/                   # Flask API
│   ├── core/                  # 检测核心逻辑
│   ├── data/                  # ROI 配置和测试数据
│   └── scripts/               # 测试脚本
├── src/main/java/             # Spring Boot 后端
│   └── com/traffic/management/
│       ├── controller/        # REST API 控制器
│       ├── service/           # 业务逻辑
│       ├── repository/        # 数据访问层
│       └── entity/            # 数据模型
├── mysql/init/                # 数据库初始化脚本
├── docker-compose.yml         # Docker 编排配置
├── LLMlqy/                    # LLM 信号控制（同学代码）
└── docs/                      # 文档和图表
```

---

## 🌐 服务地址

### 生产环境
- **后端 API**: http://47.107.50.136:8081/api
- **AI 服务**: http://47.107.50.136:5000
- **WebSocket**: ws://47.107.50.136:5000
- **数据库管理**: http://47.107.50.136:8080

### 本地开发
- **后端 API**: http://localhost:8081/api
- **AI 服务**: http://localhost:5000
- **数据库管理**: http://localhost:8080

---

## 🔑 测试账户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | password123 | 管理员 |
| police001 | password123 | 交警 |

---

## 📖 文档导航

| 文档 | 说明 | 推荐度 |
|------|------|--------|
| **[DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)** | 🌟 完整部署指南 | ⭐⭐⭐⭐⭐ |
| **[SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md)** | 系统架构详解 | ⭐⭐⭐⭐ |
| **[ai_detection/README.md](ai_detection/README.md)** | AI 检测模块说明 | ⭐⭐⭐ |

---

## 🛠️ 常用命令

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看实时日志
docker-compose logs -f

# 重启服务
docker-compose restart

# 停止服务
docker-compose down

# 更新部署
bash update.sh
```

---

## 🐛 故障排查

### 服务启动失败
```bash
# 查看错误日志
docker-compose logs <service-name>

# 重建容器
docker-compose down
docker-compose up -d --force-recreate
```

### 数据库连接失败
```bash
# 检查容器状态
docker-compose ps

# 进入 MySQL 容器
docker exec -it traffic_db mysql -uroot -p
```

### 端口占用
```bash
# 检查端口占用
netstat -ano | findstr :8081
netstat -ano | findstr :5000
```

> 更多故障排查请查看：[DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md#故障排查)

---

## 📊 数据统计

- 8 个测试路口
- 500+ 违章记录
- 90 天流量数据
- 支持日/周/月报表

---

## 📝 API 文档

主要 API 端点：

- **认证**: `POST /api/auth/login`
- **违规记录**: `GET /api/violations`
- **路口管理**: `GET /api/intersections`
- **图片检测**: `POST /detect-image`
- **视频检测**: `POST /start-realtime`
- **信号灯控制**: `GET/POST /api/multi-direction-traffic/intersections/{id}/status`

> 完整 API 文档请查看：[SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md#api接口文档)

---

## 📄 许可证

本项目为软件工程课程作业，仅供学习使用。

---

## 👥 团队成员

- 冯俊财 - 1.借助Yolov8进行违规检测 2.服务器部署及前后端通信 3.数据分析统计相关功能
- 关镜文 - 后端接口：1.违章记录管理 2.实时交通监控 3.路口状态统计
- 路清怡 - 1.前端界面总框架以及智能信号灯控制，仪表盘具体2.后端智能信号灯llm仿真3.服务器之间通信
- 黄弋涵 - 后端接口：1.用户登录注册模块 2.智能控制信号灯时长模块

---

**记住这三个命令**：
```bash
bash update.sh           # 更新服务
docker-compose logs -f   # 查看日志
docker-compose ps        # 查看状态
```

就够用了！✨
