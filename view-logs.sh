#!/bin/bash
# TrafficMind 日志查看工具
# 用法:
#   bash view-logs.sh           # 查看所有服务日志
#   bash view-logs.sh ai        # 只查看AI服务日志
#   bash view-logs.sh backend   # 只查看Backend日志
#   bash view-logs.sh db        # 只查看数据库日志

set -e

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "=========================================="
echo -e "${BLUE}TrafficMind 日志查看工具${NC}"
echo "=========================================="
echo ""

# 检查 Docker Compose 是否运行
if ! docker-compose ps | grep -q "Up"; then
    echo -e "${RED}❌ 错误: 服务未运行，请先启动服务${NC}"
    echo "   运行: docker-compose up -d"
    exit 1
fi

# 根据参数选择查看哪个服务的日志
SERVICE="${1:-all}"

case "$SERVICE" in
    ai|ai-service)
        echo -e "${GREEN}📋 查看 AI Service 日志 (实时)${NC}"
        echo -e "${YELLOW}提示: 按 Ctrl+C 退出${NC}"
        echo ""
        echo "=========================================="
        echo "关注以下关键日志："
        echo "  [信号同步] ✅ 从 LLM 获取"
        echo "  [WebSocket] 📡 准备发送LLM信号灯数据到前端"
        echo "  [WebSocket] ✅ LLM信号灯数据已发送!"
        echo "=========================================="
        echo ""
        sleep 2
        docker-compose logs -f --tail=100 ai-service
        ;;

    backend|java)
        echo -e "${GREEN}📋 查看 Backend 日志 (实时)${NC}"
        echo -e "${YELLOW}提示: 按 Ctrl+C 退出${NC}"
        echo ""
        echo "=========================================="
        echo "关注以下关键日志："
        echo "  收到数据"
        echo "  🔔 准备广播交通数据"
        echo "  📡 广播完成"
        echo "=========================================="
        echo ""
        sleep 2
        docker-compose logs -f --tail=100 backend
        ;;

    db|database|mysql)
        echo -e "${GREEN}📋 查看 MySQL 日志 (实时)${NC}"
        echo -e "${YELLOW}提示: 按 Ctrl+C 退出${NC}"
        echo ""
        sleep 2
        docker-compose logs -f --tail=100 mysql
        ;;

    redis)
        echo -e "${GREEN}📋 查看 Redis 日志 (实时)${NC}"
        echo -e "${YELLOW}提示: 按 Ctrl+C 退出${NC}"
        echo ""
        sleep 2
        docker-compose logs -f --tail=100 redis
        ;;

    all)
        echo -e "${GREEN}📋 查看所有服务日志 (实时)${NC}"
        echo -e "${YELLOW}提示: 按 Ctrl+C 退出${NC}"
        echo ""
        echo "=========================================="
        echo "服务列表："
        echo "  - ai-service (AI检测服务, 端口 5000)"
        echo "  - backend (Java后端, 端口 8081)"
        echo "  - mysql (数据库, 端口 3306)"
        echo "  - redis (缓存, 端口 6379)"
        echo "=========================================="
        echo ""
        sleep 2
        docker-compose logs -f --tail=50
        ;;

    *)
        echo -e "${RED}❌ 未知服务: $SERVICE${NC}"
        echo ""
        echo "可用选项:"
        echo "  bash view-logs.sh           # 查看所有服务"
        echo "  bash view-logs.sh ai        # AI服务 (Python)"
        echo "  bash view-logs.sh backend   # Backend服务 (Java)"
        echo "  bash view-logs.sh db        # MySQL数据库"
        echo "  bash view-logs.sh redis     # Redis缓存"
        exit 1
        ;;
esac
