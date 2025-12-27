# TrafficMind 部署指南

> 简单、快速、完整的部署文档

---

## 🚀 快速开始

### 首次部署（5分钟）

```bash
# 1. 克隆代码
git clone <仓库地址> ~/SE_project_backend
cd ~/SE_project_backend

# 2. 配置环境变量（可选，有默认值）
cp .env .env.backup  # 备份
nano .env            # 修改密码（推荐）

# 3. 启动所有服务
docker-compose up -d

# 4. 查看启动状态
docker-compose logs -f
```

### 更新部署（1分钟）

```bash
cd ~/SE_project_backend
bash update.sh  # 一键更新！
```

---

## 📋 环境要求

### 服务器配置

- **操作系统**: Ubuntu 20.04+ / CentOS 7+ / Debian 10+
- **CPU**: 2核+ (推荐4核)
- **内存**: 4GB+ (推荐8GB)
- **磁盘**: 20GB+ 可用空间
- **网络**: 稳定的网络连接

### 必需软件

只需要安装 Docker：

```bash
# 安装 Docker 和 Docker Compose
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 添加当前用户到 docker 组
sudo usermod -aG docker $USER

# 重新登录使权限生效
exit  # 退出后重新 SSH 登录

# 验证安装
docker --version
docker-compose --version
```

---

## 🏗️ 系统架构

### 服务组件

| 服务 | 容器名 | 端口 | 说明 |
|------|--------|------|------|
| MySQL 数据库 | traffic_db | 3307 | 核心业务数据 |
| Redis 缓存 | traffic_redis | 6379 | 缓存和会话 |
| MinIO 存储 | traffic_minio | 9000/9001 | 图片存储 |
| Java 后端 | traffic_backend | 8081 | 业务逻辑API |
| Python AI | traffic_ai | 5000 | AI检测服务 |
| Adminer | traffic_db_admin | 8080 | 数据库管理 |

### 服务依赖

```
traffic_backend ──→ traffic_db (MySQL)
       │       └──→ redis
       │       └──→ minio
       └──→ ai-service
              └──→ traffic_db
              └──→ redis
```

---

## 📦 首次部署详细步骤

### 1. 安装 Docker（如果未安装）

**Ubuntu/Debian**:
```bash
# 更新软件源
sudo apt update

# 安装 Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# 启动 Docker
sudo systemctl start docker
sudo systemctl enable docker

# 添加用户权限
sudo usermod -aG docker $USER
```

**CentOS/RHEL**:
```bash
# 安装 Docker
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 启动服务
sudo systemctl start docker
sudo systemctl enable docker

# 添加用户权限
sudo usermod -aG docker $USER
```

**重新登录以应用权限**:
```bash
exit  # 退出当前会话
# 重新 SSH 登录
```

### 2. 克隆项目

```bash
# 创建项目目录
mkdir -p ~/projects
cd ~/projects

# 克隆代码（替换为你的仓库地址）
git clone <你的仓库地址> SE_project_backend
cd SE_project_backend

# 查看文件
ls -la
```

### 3. 配置环境变量

查看现有配置：
```bash
cat .env
```

默认配置（已可用）：
```bash
# MySQL 配置
MYSQL_ROOT_PASSWORD=TrafficMind@2024
MYSQL_DATABASE=traffic_mind

# MinIO 配置
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin

# JWT 配置
JWT_SECRET=TrafficMindSecretKeyForJWTTokenGenerationAndValidation2024_MakeItLongerThan512BitsForHS512SecurityStandard

# 时区
TIMEZONE=Asia/Shanghai
```

**生产环境建议修改密码**：
```bash
nano .env
# 修改密码后保存（Ctrl+X, Y, Enter）
```

### 4. 检查配置

```bash
bash check-docker.sh
```

应该看到：
```
✅ 基本配置检查通过！
```

### 5. 启动服务

```bash
# 首次启动（会自动下载镜像和构建，较慢）
docker-compose up -d

# 查看启动过程
docker-compose logs -f
```

**等待所有服务启动**（约2-3分钟），看到类似输出：
```
traffic_db      | ready for connections
traffic_backend | Started TrafficManagementApplication
traffic_ai      | * Running on http://0.0.0.0:5000
```

按 `Ctrl+C` 退出日志查看（服务继续运行）

### 6. 验证服务

```bash
# 查看所有容器状态
docker-compose ps

# 应该看到所有服务都是 "Up" 状态
```

**测试服务**：
```bash
# 测试后端
curl http://localhost:8081/actuator/health

# 测试 AI 服务
curl http://localhost:5000/health
```

### 7. 配置防火墙（如果需要外网访问）

**Ubuntu (UFW)**:
```bash
sudo ufw allow 8081/tcp  # Backend API
sudo ufw allow 5000/tcp  # AI Service
sudo ufw allow 8080/tcp  # Adminer（可选）
sudo ufw reload
```

**CentOS (firewalld)**:
```bash
sudo firewall-cmd --permanent --add-port=8081/tcp
sudo firewall-cmd --permanent --add-port=5000/tcp
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

### 8. 访问服务

- **后端 API**: `http://服务器IP:8081/actuator/health`
- **AI 服务**: `http://服务器IP:5000/health`
- **数据库管理**: `http://服务器IP:8080` (Adminer)
  - 服务器: `traffic-db`
  - 用户名: `root`
  - 密码: `.env` 中的 `MYSQL_ROOT_PASSWORD`
  - 数据库: `traffic_mind`
- **MinIO 控制台**: `http://服务器IP:9001`
  - 用户名: `.env` 中的 `MINIO_ROOT_USER`
  - 密码: `.env` 中的 `MINIO_ROOT_PASSWORD`

---

## 🔄 更新部署

### 方式1: 使用自动脚本（推荐）⭐

```bash
cd ~/projects/SE_project_backend
bash update.sh
```

脚本会自动完成：
1. ✅ 拉取最新代码 (`git pull`)
2. ✅ 检查配置
3. ✅ 停止服务
4. ✅ 重新构建镜像
5. ✅ 启动服务
6. ✅ 健康检查

### 方式2: 手动执行

```bash
cd ~/projects/SE_project_backend

# 拉取最新代码
git pull origin main

# 停止服务
docker-compose down

# 重新构建
docker-compose build

# 启动服务
docker-compose up -d

# 查看日志
docker-compose logs -f
```

### 方式3: 只更新特定服务

```bash
# 只更新 AI 服务
docker-compose build ai-service
docker-compose up -d --no-deps ai-service

# 只更新 Backend
docker-compose build backend
docker-compose up -d --no-deps backend
```

---

## 🔧 常用运维命令

### 查看服务状态

```bash
# 查看所有容器
docker-compose ps

# 查看资源使用
docker stats
```

### 查看日志

```bash
# 查看所有日志
docker-compose logs -f

# 查看特定服务
docker-compose logs -f backend
docker-compose logs -f ai-service

# 查看最近100行
docker-compose logs --tail=100 backend
```

### 重启服务

```bash
# 重启所有服务
docker-compose restart

# 重启特定服务
docker-compose restart backend
docker-compose restart ai-service
```

### 停止服务

```bash
# 停止所有服务（保留数据）
docker-compose down

# 停止并删除数据（危险！）
docker-compose down -v
```

### 进入容器调试

```bash
# 进入 Backend 容器
docker exec -it traffic_backend sh

# 进入 AI 容器
docker exec -it traffic_ai bash

# 连接 MySQL
docker exec -it traffic_db mysql -u root -p

# 连接 Redis
docker exec -it traffic_redis redis-cli
```

---

## 🔍 故障排查

### 问题1: 服务启动失败

```bash
# 查看错误日志
docker-compose logs backend
docker-compose logs ai-service

# 检查容器状态
docker-compose ps

# 重启服务
docker-compose restart backend
```

### 问题2: 端口被占用

```bash
# 查看端口占用
sudo netstat -tulpn | grep :8081

# 或者使用 lsof
sudo lsof -i :8081

# 停止占用进程
sudo kill -9 <PID>
```

### 问题3: 数据库连接失败

```bash
# 检查 MySQL 状态
docker exec traffic_db mysqladmin ping -h localhost -u root -p

# 查看数据库日志
docker-compose logs traffic-db

# 手动连接测试
docker exec -it traffic_db mysql -u root -p
```

### 问题4: 磁盘空间不足

```bash
# 查看磁盘使用
df -h

# 查看 Docker 占用
docker system df

# 清理未使用的镜像和容器
docker system prune -a
```

### 问题5: Git pull 冲突

```bash
# 如果本地没有重要修改，直接覆盖
git fetch origin
git reset --hard origin/main
```

---

## 📊 监控和维护

### 查看资源使用

```bash
# 实时监控所有容器
docker stats

# 只监控特定容器
docker stats traffic_backend traffic_ai
```

### 数据备份

```bash
# 备份数据库
docker exec traffic_db mysqldump -u root -pTrafficMind@2024 --all-databases > backup_$(date +%Y%m%d).sql

# 备份上传文件
tar -czf uploads_backup_$(date +%Y%m%d).tar.gz uploads/

# 恢复数据库
docker exec -i traffic_db mysql -u root -pTrafficMind@2024 < backup_20241227.sql
```

### 定期清理

```bash
# 清理旧日志（保留最近7天）
find logs/ -name "*.log" -mtime +7 -delete

# 清理 Docker 缓存
docker image prune -a
docker container prune
```

---

## 🔐 安全建议

### 1. 修改默认密码

生产环境务必修改 `.env` 中的所有密码：
```bash
# 生成强密码
openssl rand -base64 32
```

### 2. 限制数据库访问

编辑 `docker-compose.yml`，只允许本地访问：
```yaml
traffic-db:
  ports:
    - "127.0.0.1:3307:3306"  # 只允许本地访问
```

### 3. 使用 HTTPS

配置 Nginx 反向代理（可选）：
```bash
sudo apt install nginx certbot python3-certbot-nginx
sudo certbot --nginx -d yourdomain.com
```

### 4. 定期更新

```bash
# 每周更新一次
cd ~/projects/SE_project_backend
bash update.sh
```

---

## 📝 快速命令参考

```bash
# 📥 更新部署
bash update.sh

# 👀 查看日志
docker-compose logs -f

# 📊 查看状态
docker-compose ps

# 🔄 重启服务
docker-compose restart

# ⏹️  停止服务
docker-compose down

# 🚀 启动服务
docker-compose up -d

# 🔍 检查配置
bash check-docker.sh

# 💾 备份数据
docker exec traffic_db mysqldump -u root -p --all-databases > backup.sql
```

---

## 📚 相关文档

- [QUICK_START.md](QUICK_START.md) - 5分钟快速启动
- [SIGNAL_CHANGES.md](SIGNAL_CHANGES.md) - 信号灯配置说明
- [ai_detection/README.md](ai_detection/README.md) - AI检测文档

---

## 🆘 获取帮助

遇到问题？
1. 查看本文档的故障排查部分
2. 查看服务日志: `docker-compose logs -f`
3. 检查配置: `bash check-docker.sh`
4. 联系团队: Coders - 冯俊财

---

**版本**: 2.0.0
**更新**: 2025-12-27
**团队**: Coders
