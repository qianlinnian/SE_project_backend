#!/bin/bash
# TrafficMind 快速更新脚本
# 用法:
#   bash update.sh           # 正常更新（使用缓存，快速）
#   bash update.sh --no-cache # 强制重建（不使用缓存，慢但干净）

set -e  # 遇到错误立即退出

# 检查是否需要强制重建
NO_CACHE=""
if [[ "$1" == "--no-cache" ]]; then
    NO_CACHE="--no-cache"
    echo "=========================================="
    echo "TrafficMind 完全重建模式"
    echo "=========================================="
else
    echo "=========================================="
    echo "TrafficMind 快速更新"
    echo "=========================================="
fi
echo ""

# 检查是否在项目目录
if [ ! -f "docker-compose.yml" ]; then
    echo "❌ 错误: 请在项目根目录运行此脚本"
    exit 1
fi

# 1. 拉取最新代码
echo "📥 1. 拉取最新代码..."
git pull origin main || {
    echo "❌ Git pull 失败，请检查网络或解决冲突"
    exit 1
}

echo ""
echo "✅ 代码更新成功"
echo ""

# 2. 检查配置
echo "🔍 2. 检查配置..."
if [ -f "check-docker.sh" ]; then
    bash check-docker.sh
else
    echo "⚠️  check-docker.sh 不存在，跳过检查"
fi

echo ""

# 3. 询问是否继续
read -p "是否继续更新服务? (y/n) " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ 更新已取消"
    exit 0
fi

# 4. 停止服务
echo ""
echo "⏹️  3. 停止现有服务..."
docker-compose down

# 5. 配置 Docker 构建加速（可选）
echo ""
echo "⚡ 4. 配置 Docker 构建加速..."

# 检查并配置 Docker 镜像加速
if [ -f "/etc/docker/daemon.json" ]; then
    echo "   ℹ️  Docker 配置已存在"
else
    echo "   ⚠️  建议配置 Docker Hub 镜像加速"
    echo "      参考: https://developer.aliyun.com/mirror/docker-ce"
fi

# 设置构建参数（使用 BuildKit 加速）
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

echo "   ✅ BuildKit 已启用"

# 6. 智能构建镜像
echo ""
echo "🔨 5. 重新构建镜像..."
echo ""

unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY
# 构建 AI 服务
echo "   📦 构建 AI 服务 (ai-service)..."
if docker-compose build $NO_CACHE ai-service; then
    echo "   ✅ AI 服务构建成功"
else
    echo "   ❌ AI 服务构建失败"
    exit 1
fi

echo ""

# 构建 Backend 服务
echo "   📦 构建 Backend 服务 (backend)..."
if docker-compose build $NO_CACHE backend; then
    echo "   ✅ Backend 服务构建成功"
else
    echo "   ⚠️  Backend 服务构建失败，跳过（如果已有镜像会使用现有镜像）"
fi

echo ""
if [[ -z "$NO_CACHE" ]]; then
    echo "   💡 提示: 使用缓存加速构建，如遇问题请运行: bash update.sh --no-cache"
else
    echo "   ✅ 已完全重建所有镜像"
fi

# 7. 启动服务
echo ""
echo "🚀 6. 启动服务..."
docker compose up -d || {
    echo "❌ 启动服务失败，请检查日志"
    exit 1
}

# 8. 等待服务就绪
echo ""
echo "⏳ 7. 等待服务启动..."
sleep 10

# 9. 显示服务状态
echo ""
echo "📊 8. 服务状态:"
docker-compose ps

# 10. 健康检查
echo ""
echo "🏥 9. 健康检查:"

# 检查 Backend
if curl -f http://localhost:8081/actuator/health &>/dev/null; then
    echo "   ✅ Backend (8081) - 运行正常"
else
    echo "   ⚠️  Backend (8081) - 未就绪，请稍候再试"
fi

# 检查 AI Service
if curl -f http://localhost:5000/health &>/dev/null; then
    echo "   ✅ AI Service (5000) - 运行正常"
else
    echo "   ⚠️  AI Service (5000) - 未就绪，请稍候再试"
fi

echo ""
echo "=========================================="
echo "✅ 更新完成！"
echo "=========================================="
echo ""
echo "📝 有用的命令:"
echo "   查看日志: docker-compose logs -f"
echo "   查看状态: docker-compose ps"
echo "   重启服务: docker-compose restart"
echo "   停止服务: docker-compose down"
echo ""


# # 进入MySQL容器
# docker exec -it traffic_db mysql -uroot -pTrafficMind@2024 traffic_mind

# # 查询历史记录
# SELECT id, simulation_timestamp, step, control_mode, total_queue, total_vehicles, created_at 
# FROM traffic_flow_records 
# ORDER BY created_at DESC 
# LIMIT 10;
