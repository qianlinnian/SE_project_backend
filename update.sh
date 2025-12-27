#!/bin/bash
# TrafficMind 快速更新脚本

set -e  # 遇到错误立即退出

echo "=========================================="
echo "TrafficMind 快速更新"
echo "=========================================="
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

# 5. 重新构建镜像
echo ""
echo "🔨 4. 重新构建镜像..."
docker-compose build

# 6. 启动服务
echo ""
echo "🚀 5. 启动服务..."
docker-compose up -d

# 7. 等待服务就绪
echo ""
echo "⏳ 6. 等待服务启动..."
sleep 10

# 8. 显示服务状态
echo ""
echo "📊 7. 服务状态:"
docker-compose ps

# 9. 健康检查
echo ""
echo "🏥 8. 健康检查:"

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
