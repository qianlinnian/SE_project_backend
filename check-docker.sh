#!/bin/bash
# Docker 配置检查脚本

echo "=========================================="
echo "TrafficMind Docker 配置检查"
echo "=========================================="
echo ""

# 检查必要文件
echo "📋 1. 检查必要文件..."
files=(
    "docker-compose.yml"
    "Dockerfile"
    "ai_detection/Dockerfile"
    "ai_detection/requirements.txt"
    "ai_detection/api/ai_realtime_service.py"
    ".dockerignore"
    ".env"
)

all_exist=true
for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        echo "   ✅ $file"
    else
        echo "   ❌ $file (缺失)"
        all_exist=false
    fi
done

echo ""
echo "📦 2. 检查 AI 检测依赖..."
if [ -f "ai_detection/requirements.txt" ]; then
    echo "   依赖包数量: $(grep -v '^#' ai_detection/requirements.txt | grep -v '^$' | wc -l)"
    echo "   关键依赖:"
    grep -E "opencv|ultralytics|flask|socketio" ai_detection/requirements.txt | sed 's/^/     /'
else
    echo "   ❌ requirements.txt 不存在"
fi

echo ""
echo "🔧 3. 检查环境变量..."
if [ -f ".env" ]; then
    echo "   ✅ .env 文件存在"
    required_vars=("MYSQL_ROOT_PASSWORD" "MYSQL_DATABASE" "JWT_SECRET")
    for var in "${required_vars[@]}"; do
        if grep -q "^$var=" .env; then
            echo "   ✅ $var 已配置"
        else
            echo "   ⚠️  $var 未配置"
        fi
    done
else
    echo "   ❌ .env 文件不存在"
    echo "   请从 .env.example 复制并修改"
fi

echo ""
echo "🐳 4. 检查 Docker 服务..."
if command -v docker &> /dev/null; then
    echo "   ✅ Docker 已安装: $(docker --version)"
    if docker info &> /dev/null; then
        echo "   ✅ Docker 服务运行中"
    else
        echo "   ❌ Docker 服务未运行"
    fi
else
    echo "   ❌ Docker 未安装"
fi

if command -v docker-compose &> /dev/null; then
    echo "   ✅ Docker Compose 已安装: $(docker-compose --version)"
else
    echo "   ❌ Docker Compose 未安装"
fi

echo ""
echo "📁 5. 检查目录结构..."
dirs=("mysql/init" "logs" "uploads")
for dir in "${dirs[@]}"; do
    if [ -d "$dir" ]; then
        echo "   ✅ $dir/"
    else
        echo "   ⚠️  $dir/ (将自动创建)"
    fi
done

echo ""
echo "🔍 6. 检查 Dockerfile 配置..."
echo "   Backend Dockerfile:"
if grep -q "FROM eclipse-temurin:17" Dockerfile; then
    echo "     ✅ Java 17 基础镜像"
fi
if grep -q "EXPOSE 8081" Dockerfile; then
    echo "     ✅ 端口 8081"
fi

echo "   AI Service Dockerfile:"
if grep -q "FROM python:3.10" ai_detection/Dockerfile; then
    echo "     ✅ Python 3.10 基础镜像"
fi
if grep -q "EXPOSE 5000" ai_detection/Dockerfile; then
    echo "     ✅ 端口 5000"
fi
if grep -q "COPY ai_detection/" ai_detection/Dockerfile; then
    echo "     ✅ 正确的构建上下文"
else
    echo "     ⚠️  构建上下文可能不正确"
fi

echo ""
echo "=========================================="
if [ "$all_exist" = true ]; then
    echo "✅ 基本配置检查通过！"
    echo ""
    echo "下一步："
    echo "1. 确保 .env 文件已配置"
    echo "2. 运行: docker-compose up -d"
    echo "3. 查看日志: docker-compose logs -f"
else
    echo "⚠️  发现缺失文件，请补充后再启动"
fi
echo "=========================================="
