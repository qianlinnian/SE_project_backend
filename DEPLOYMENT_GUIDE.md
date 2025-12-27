# TrafficMind 交通智脑 - 部署指南

## 📋 部署就绪状态检查

### ✅ 已完成的部分

- [x] **核心功能完整**
  - [x] YOLOv8 车辆检测
  - [x] 违规检测（闯红灯、逆行、跨实线）
  - [x] 实时视频流处理
  - [x] 图片违规检测
  - [x] WebSocket 实时通信
  - [x] JWT 认证系统
  - [x] 文件上传下载
  - [x] 违规记录管理

- [x] **数据库设计**
  - [x] MySQL 表结构完整 (9个初始化SQL文件)
  - [x] 用户表、违规表、路口方向表等
  - [x] 索引优化
  - [x] 种子数据

- [x] **API 接口**
  - [x] Python Flask API (端口 5000)
  - [x] Java Spring Boot API (端口 8081)
  - [x] 跨服务通信机制
  - [x] 异步处理机制

- [x] **配置文件**
  - [x] `application.properties` (生产就绪)
  - [x] `docker-compose.yml` (包含 MySQL, Redis, MinIO)
  - [x] `.env` 环境变量支持
  - [x] 相对路径文件存储 (跨平台)

- [x] **依赖管理**
  - [x] Python `requirements.txt`
  - [x] Java `pom.xml` / Gradle

### ⚠️ 需要完善的部分

- [ ] **Docker 镜像**
  - [ ] Python AI 服务 Dockerfile
  - [ ] Java 后端 Dockerfile
  - [ ] 前端 Dockerfile
  - [ ] 完整的 docker-compose.yml (包含应用服务)

- [ ] **信号灯配置**
  - [ ] `intersection_directions` 表初始化数据
  - [ ] 默认路口信号配置

- [ ] **环境变量配置**
  - [ ] `.env` 文件模板
  - [ ] 生产环境配置示例

- [ ] **监控和日志**
  - [ ] 日志收集配置
  - [ ] 性能监控
  - [ ] 错误追踪

---

## 🚀 快速部署步骤

### 方案 1: Docker 部署 (推荐)

#### 前提条件
- Docker 20.10+
- Docker Compose 2.0+
- 服务器内存 ≥ 8GB
- 硬盘空间 ≥ 50GB

#### 步骤

**1. 克隆代码到服务器**
```bash
git clone <your-repo-url> traffic-mind
cd traffic-mind
```

**2. 创建环境变量文件**
```bash
cat > .env << 'EOF'
# 数据库配置
MYSQL_ROOT_PASSWORD=TrafficMind@2024
MYSQL_DATABASE=traffic_mind
MYSQL_PORT=3307

# Redis配置
REDIS_PORT=6379

# MinIO配置
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin123
MINIO_PORT_API=9000
MINIO_PORT_CONSOLE=9001

# 时区
TIMEZONE=Asia/Shanghai

# 应用配置
JAVA_BACKEND_PORT=8081
PYTHON_AI_PORT=5000
FRONTEND_PORT=80

# JWT密钥（生产环境请更换为随机字符串）
JWT_SECRET=TrafficMindSecretKeyForJWTTokenGenerationAndValidation2024_MakeItLongerThan512BitsForHS512SecurityStandard

# 文件上传路径
UPLOAD_BASE_PATH=./uploads
EOF
```

**3. 启动基础设施 (MySQL, Redis, MinIO)**
```bash
docker-compose up -d
```

**4. 等待数据库初始化完成**
```bash
# 查看日志，等待 "ready for connections" 出现
docker logs -f traffic_db

# 或使用健康检查
docker-compose ps
```

**5. 初始化信号灯配置数据**
```bash
# 连接到数据库
docker exec -it traffic_db mysql -uroot -pTrafficMind@2024 traffic_mind

# 执行以下SQL（如果没有自动创建）
INSERT INTO intersection_directions
(intersection_id, direction, direction_name, lane_count, has_turn_lane,
 straight_red_duration, straight_yellow_duration, straight_green_duration,
 left_turn_red_duration, left_turn_yellow_duration, left_turn_green_duration,
 right_turn_red_duration, right_turn_yellow_duration, right_turn_green_duration,
 current_straight_phase, current_left_turn_phase, current_right_turn_phase,
 straight_phase_remaining, left_turn_phase_remaining, right_turn_phase_remaining,
 priority_level, traffic_weight, created_at, updated_at)
VALUES
-- 北向（NORTH）
(1, 'NORTH', '北向', 3, TRUE, 40, 3, 30, 50, 3, 20, 5, 3, 60, 'RED', 'RED', 'RED', 0, 0, 0, 1, 1.0, NOW(), NOW()),
-- 南向（SOUTH）
(1, 'SOUTH', '南向', 3, TRUE, 40, 3, 30, 50, 3, 20, 5, 3, 60, 'RED', 'RED', 'RED', 0, 0, 0, 1, 1.0, NOW(), NOW()),
-- 东向（EAST）
(1, 'EAST', '东向', 3, TRUE, 30, 3, 40, 60, 3, 10, 5, 3, 60, 'GREEN', 'RED', 'RED', 0, 0, 0, 1, 1.0, NOW(), NOW()),
-- 西向（WEST）
(1, 'WEST', '西向', 3, TRUE, 30, 3, 40, 60, 3, 10, 5, 3, 60, 'GREEN', 'RED', 'RED', 0, 0, 0, 1, 1.0, NOW(), NOW());

-- 退出
exit;
```

**6. 启动 Python AI 服务**

方式A: 直接运行（开发环境）
```bash
cd ai_detection
pip install -r requirements.txt
python api/ai_realtime_service.py
```

方式B: Docker运行（生产环境）
```bash
# 先创建 Dockerfile（见下文）
docker build -t traffic-mind-ai:latest -f ai_detection/Dockerfile .
docker run -d \
  --name traffic_ai \
  --network traffic-network \
  -p 5000:5000 \
  -v $(pwd)/uploads:/app/uploads \
  -v $(pwd)/ai_detection/data:/app/data \
  traffic-mind-ai:latest
```

**7. 启动 Java 后端服务**

方式A: 直接运行
```bash
./mvnw clean package -DskipTests
java -jar target/traffic-management-system-*.jar
```

方式B: Docker运行
```bash
# 创建 Dockerfile（见下文）
docker build -t traffic-mind-backend:latest .
docker run -d \
  --name traffic_backend \
  --network traffic-network \
  -p 8081:8081 \
  -v $(pwd)/uploads:/app/uploads \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://traffic-db:3306/traffic_mind \
  -e SPRING_REDIS_HOST=redis \
  traffic-mind-backend:latest
```

**8. 验证部署**
```bash
# 检查服务状态
curl http://localhost:5000/health
curl http://localhost:8081/actuator/health

# 测试登录
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}'
```

---

### 方案 2: 手动部署（传统服务器）

#### 前提条件
- Linux 服务器 (Ubuntu 20.04+ / CentOS 8+)
- Python 3.10+
- Java 17+
- MySQL 8.0+
- Redis 7.0+
- Nginx (可选)

#### 步骤

**1. 安装依赖**
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install -y python3.10 python3-pip openjdk-17-jdk mysql-server redis-server nginx

# CentOS/RHEL
sudo yum install -y python3 python3-pip java-17-openjdk mysql-server redis nginx
```

**2. 配置 MySQL**
```bash
sudo systemctl start mysql
sudo systemctl enable mysql

# 创建数据库和用户
sudo mysql -u root << EOF
CREATE DATABASE traffic_mind CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'traffic_user'@'localhost' IDENTIFIED BY 'TrafficMind@2024';
GRANT ALL PRIVILEGES ON traffic_mind.* TO 'traffic_user'@'localhost';
FLUSH PRIVILEGES;
EOF

# 导入初始化脚本
cd mysql/init
for file in *.sql; do
  sudo mysql -u root traffic_mind < "$file"
done
```

**3. 配置 Redis**
```bash
sudo systemctl start redis
sudo systemctl enable redis
```

**4. 部署 Python AI 服务**
```bash
cd /opt/traffic-mind/ai_detection

# 创建虚拟环境
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 创建 systemd 服务
sudo tee /etc/systemd/system/traffic-ai.service << EOF
[Unit]
Description=TrafficMind AI Detection Service
After=network.target

[Service]
Type=simple
User=traffic
WorkingDirectory=/opt/traffic-mind/ai_detection
Environment="PATH=/opt/traffic-mind/ai_detection/venv/bin"
ExecStart=/opt/traffic-mind/ai_detection/venv/bin/python api/ai_realtime_service.py
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl start traffic-ai
sudo systemctl enable traffic-ai
```

**5. 部署 Java 后端**
```bash
cd /opt/traffic-mind

# 打包应用
./mvnw clean package -DskipTests

# 创建 systemd 服务
sudo tee /etc/systemd/system/traffic-backend.service << EOF
[Unit]
Description=TrafficMind Backend Service
After=network.target mysql.service redis.service

[Service]
Type=simple
User=traffic
WorkingDirectory=/opt/traffic-mind
ExecStart=/usr/bin/java -jar target/traffic-management-system-1.0.0.jar
Restart=always
RestartSec=10
Environment="SPRING_PROFILES_ACTIVE=prod"

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl start traffic-backend
sudo systemctl enable traffic-backend
```

**6. 配置 Nginx 反向代理**
```bash
sudo tee /etc/nginx/sites-available/traffic-mind << 'EOF'
upstream backend {
    server localhost:8081;
}

upstream ai_service {
    server localhost:5000;
}

server {
    listen 80;
    server_name traffic-mind.example.com;

    # 前端静态文件
    location / {
        root /opt/traffic-mind/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    # Java 后端 API
    location /api/ {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Python AI 服务
    location /ai/ {
        proxy_pass http://ai_service/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket 支持
    location /socket.io/ {
        proxy_pass http://ai_service/socket.io/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }

    # 文件上传大小限制
    client_max_body_size 100M;
}
EOF

sudo ln -s /etc/nginx/sites-available/traffic-mind /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

---

## 📦 Dockerfile 示例

### Python AI 服务 Dockerfile

创建 `ai_detection/Dockerfile`:

```dockerfile
FROM python:3.10-slim

# 安装系统依赖
RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 复制依赖文件
COPY ai_detection/requirements.txt .

# 安装 Python 依赖
RUN pip install --no-cache-dir -r requirements.txt

# 复制应用代码
COPY ai_detection/ .

# 创建必要目录
RUN mkdir -p /app/uploads /app/output/screenshots

# 暴露端口
EXPOSE 5000

# 启动命令
CMD ["python", "api/ai_realtime_service.py"]
```

### Java 后端 Dockerfile

创建 `Dockerfile`:

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# 复制 Maven/Gradle 文件
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# 下载依赖（利用 Docker 缓存）
RUN ./mvnw dependency:go-offline

# 复制源代码
COPY src ./src

# 打包应用
RUN ./mvnw clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制构建产物
COPY --from=builder /app/target/*.jar app.jar

# 创建上传目录
RUN mkdir -p /app/uploads

# 暴露端口
EXPOSE 8081

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 完整的 docker-compose.yml

更新现有的 `docker-compose.yml`，添加应用服务：

```yaml
version: '3.8'

services:
  # MySQL 数据库
  traffic-db:
    image: mysql:8.0
    container_name: traffic_db
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      TZ: ${TIMEZONE}
    ports:
      - "3307:3306"
    volumes:
      - ./mysql/data:/var/lib/mysql
      - ./mysql/init:/docker-entrypoint-initdb.d
    networks:
      - traffic-network
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  # Redis 缓存
  redis:
    image: redis:7-alpine
    container_name: traffic_redis
    restart: always
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - ./redis/data:/data
    networks:
      - traffic-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # MinIO 对象存储
  minio:
    image: minio/minio:latest
    container_name: traffic_minio
    restart: always
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    volumes:
      - ./minio/data:/minio/data
    networks:
      - traffic-network
    command: server /minio/data --console-address ":9001"

  # Python AI 服务
  ai-service:
    build:
      context: .
      dockerfile: ai_detection/Dockerfile
    container_name: traffic_ai
    restart: always
    ports:
      - "5000:5000"
    volumes:
      - ./uploads:/app/uploads
      - ./ai_detection/data:/app/data
    networks:
      - traffic-network
    depends_on:
      - traffic-db
      - redis
    environment:
      - BACKEND_API_URL=http://backend:8081/api

  # Java 后端服务
  backend:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: traffic_backend
    restart: always
    ports:
      - "8081:8081"
    volumes:
      - ./uploads:/app/uploads
    networks:
      - traffic-network
    depends_on:
      traffic-db:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      - SPRING_DATASOURCE_URL=jdbc:mysql://traffic-db:3306/traffic_mind
      - SPRING_DATASOURCE_USERNAME=root
      - SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD}
      - SPRING_REDIS_HOST=redis
      - JWT_SECRET=${JWT_SECRET}
      - AI_SERVICE_BASE_URL=http://ai-service:5000

  # 前端服务（可选）
  frontend:
    build:
      context: ./ai_detection/frontend-examples/vite-app
      dockerfile: Dockerfile
    container_name: traffic_frontend
    restart: always
    ports:
      - "80:80"
    networks:
      - traffic-network
    depends_on:
      - backend
      - ai-service

networks:
  traffic-network:
    driver: bridge
```

---

## 🔧 生产环境配置建议

### 1. 环境变量配置

创建 `application-prod.properties`:

```properties
# 生产环境配置
spring.profiles.active=prod

# 数据库连接池
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000

# JPA 配置
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# 日志级别
logging.level.com.traffic.management=INFO
logging.level.org.springframework=WARN

# 文件存储（使用环境变量）
file.upload.base-path=${UPLOAD_BASE_PATH:/var/lib/traffic-mind/uploads}
file.url.base-url=${FILE_URL_BASE:http://your-domain.com/api/files}

# AI服务地址（内网）
ai.service.base-url=${AI_SERVICE_URL:http://ai-service:5000}

# JWT密钥（从环境变量读取）
jwt.secret=${JWT_SECRET}
jwt.expiration=604800000
```

### 2. 安全加固

```bash
# 修改默认密码
- 数据库密码
- Redis 密码
- JWT 密钥
- MinIO 访问密钥

# 启用 HTTPS
certbot --nginx -d your-domain.com

# 防火墙配置
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw deny 3307/tcp  # 只允许内网访问
sudo ufw deny 6379/tcp
sudo ufw enable
```

### 3. 性能优化

```properties
# JVM 参数
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 数据库索引
CREATE INDEX idx_violations_occurred_at ON violations(occurred_at);
CREATE INDEX idx_violations_status ON violations(status);
CREATE INDEX idx_violations_intersection ON violations(intersection_id);

# Redis 持久化
redis-server --appendonly yes --appendfsync everysec
```

---

## ✅ 部署前检查清单

### 必须完成：
- [ ] 修改所有默认密码
- [ ] 配置 JWT 密钥
- [ ] 创建 `.env` 文件
- [ ] 初始化数据库
- [ ] 插入信号灯配置数据
- [ ] 测试所有 API 接口
- [ ] 确认文���上传功能正常
- [ ] 测试 WebSocket 连接

### 建议完成：
- [ ] 配置域名和 SSL 证书
- [ ] 设置日志收集（ELK/Loki）
- [ ] 配置监控（Prometheus + Grafana）
- [ ] 设置自动备份（数据库/文件）
- [ ] 配置告警通知
- [ ] 压力测试

---

## 🐛 常见问题排查

### 1. 数据库连接失败
```bash
# 检查数据库是否启动
docker ps | grep traffic_db

# 查看日志
docker logs traffic_db

# 测试连接
mysql -h 127.0.0.1 -P 3307 -u root -pTrafficMind@2024
```

### 2. Redis 连接失败
```bash
# 测试 Redis
redis-cli -h localhost -p 6379 ping

# 查看 Redis 日志
docker logs traffic_redis
```

### 3. AI 服务无法连接
```bash
# 检查端口
curl http://localhost:5000/health

# 查看日志
docker logs traffic_ai

# 检查网络
docker network inspect traffic-network
```

### 4. 文件上传失败
```bash
# 检查目录权限
ls -la ./uploads

# 创建必要目录
mkdir -p ./uploads/violation

# 修改权限
chmod 755 ./uploads
```

---

## 📞 支持与维护

### 日志位置
- Java 后端: `./logs/application.log`
- Python AI: `./ai_detection/logs/ai_service.log`
- Nginx: `/var/log/nginx/access.log`

### 重启服务
```bash
# Docker 方式
docker-compose restart

# Systemd 方式
sudo systemctl restart traffic-backend
sudo systemctl restart traffic-ai
```

### 数据备份
```bash
# 备份数据库
docker exec traffic_db mysqldump -u root -pTrafficMind@2024 traffic_mind > backup_$(date +%Y%m%d).sql

# 备份文件
tar -czf uploads_backup_$(date +%Y%m%d).tar.gz ./uploads
```

---

**部署成功后访问**:
- 前端: `http://your-server-ip:80`
- 后端 API: `http://your-server-ip:8081/api`
- AI 服务: `http://your-server-ip:5000`
- 数据库管理: `http://your-server-ip:8080` (Adminer)
- MinIO 控制台: `http://your-server-ip:9001`

---

**文档版本**: 1.0.0
**最后更新**: 2025-12-27
**维护团队**: Coders (冯俊财、关镜文、路清怡、黄弋涵)
