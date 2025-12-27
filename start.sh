#!/bin/bash
# ============================================
# TrafficMind 快速启动脚本
# ============================================

set -e

echo "============================================"
echo "  TrafficMind 交通智脑 - 启动脚本"
echo "============================================"
echo ""

# 检查 .env 文件
if [ ! -f .env ]; then
    echo "❌ 错误：.env 文件不存在"
    echo "请创建 .env 文件并配置必要的环境变量"
    exit 1
fi

echo "✅ 环境变量文件检测通过"
echo ""

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "❌ 错误：未安装 Docker"
    echo "请先安装 Docker: https://docs.docker.com/get-docker/"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ 错误：未安装 Docker Compose"
    echo "请先安装 Docker Compose: https://docs.docker.com/compose/install/"
    exit 1
fi

echo "✅ Docker 环境检测通过"
echo ""

# 创建必要的目录
echo "📁 创建必要的目录..."
mkdir -p mysql/data
mkdir -p mysql/init
mkdir -p redis/data
mkdir -p minio/data
mkdir -p uploads/violation
mkdir -p uploads/general
mkdir -p logs/ai
mkdir -p logs/backend

echo "✅ 目录创建完成"
echo ""

# 询问启动模式
echo "请选择启动模式："
echo "  1) 完整启动（基础设施 + 应用服务）"
echo "  2) 仅启动基础设施（MySQL + Redis + MinIO）"
echo "  3) 停止所有服务"
echo "  4) 重启所有服务"
echo "  5) 查看日志"
echo ""
read -p "请输入选项 [1-5]: " choice

case $choice in
    1)
        echo ""
        echo "🚀 启动所有服务..."
        docker-compose up -d
        echo ""
        echo "✅ 所有服务已启动！"
        echo ""
        echo "📊 服务访问地址："
        echo "  - 前端: http://localhost:80"
        echo "  - Java 后端: http://localhost:8081"
        echo "  - Python AI: http://localhost:5000"
        echo "  - 数据库管理: http://localhost:8080"
        echo "  - MinIO 控制台: http://localhost:9001"
        echo ""
        echo "📝 查看日志："
        echo "  docker-compose logs -f"
        echo ""
        echo "🔍 检查服务状态："
        echo "  docker-compose ps"
        ;;
    2)
        echo ""
        echo "🚀 启动基础设施服务..."
        docker-compose up -d traffic-db redis minio db-admin
        echo ""
        echo "✅ 基础设施服务已启动！"
        echo ""
        echo "📊 服务访问地址："
        echo "  - MySQL: localhost:3307"
        echo "  - Redis: localhost:6379"
        echo "  - MinIO API: localhost:9000"
        echo "  - MinIO 控制台: http://localhost:9001"
        echo "  - 数据库管理: http://localhost:8080"
        ;;
    3)
        echo ""
        echo "🛑 停止所有服务..."
        docker-compose down
        echo ""
        echo "✅ 所有服务已停止！"
        ;;
    4)
        echo ""
        echo "🔄 重启所有服务..."
        docker-compose restart
        echo ""
        echo "✅ 所有服务已重启！"
        ;;
    5)
        echo ""
        echo "📋 实时日志（按 Ctrl+C 退出）："
        docker-compose logs -f
        ;;
    *)
        echo ""
        echo "❌ 无效的选项"
        exit 1
        ;;
esac

echo ""
echo "============================================"
echo "  启动完成！"
echo "============================================"
