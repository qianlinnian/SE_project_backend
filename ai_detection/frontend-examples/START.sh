#!/bin/bash

echo "========================================"
echo "   AI 交通检测 - 前端启动向导"
echo "========================================"
echo

function show_menu() {
    echo "请选择启动方式:"
    echo
    echo "  [1] 打开 HTML 演示 (最简单)"
    echo "  [2] 启动 Vite + React 开发服务器 (推荐)"
    echo "  [3] 查看启动文档"
    echo "  [0] 退出"
    echo
}

function open_html() {
    echo
    echo "正在打开 HTML 演示..."

    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        open demo.html
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        # Linux
        xdg-open demo.html
    else
        echo "请手动打开 demo.html"
    fi

    echo
    echo "✅ 已在浏览器中打开 demo.html"
    echo "💡 请确保后端已启动: python ai_detection/api/detection_api.py"
    echo
    read -p "按 Enter 继续..."
}

function start_vite() {
    echo
    echo "正在启动 Vite 开发服务器..."
    echo
    cd vite-app

    if [ ! -d "node_modules" ]; then
        echo "⚠️  未找到 node_modules，正在安装依赖..."
        echo
        npm install
        echo
    fi

    echo
    echo "🚀 启动开发服务器..."
    npm run dev
}

function open_docs() {
    echo
    echo "正在打开启动文档..."

    if [[ "$OSTYPE" == "darwin"* ]]; then
        open GETTING_STARTED.md
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        xdg-open GETTING_STARTED.md
    else
        cat GETTING_STARTED.md
    fi

    echo
    echo "✅ 已打开 GETTING_STARTED.md"
    echo
    read -p "按 Enter 继续..."
}

while true; do
    show_menu
    read -p "请输入选项 (0-3): " choice

    case $choice in
        1)
            open_html
            break
            ;;
        2)
            start_vite
            break
            ;;
        3)
            open_docs
            ;;
        0)
            echo
            echo "感谢使用！👋"
            sleep 1
            break
            ;;
        *)
            echo "无效选项，请重新选择"
            echo
            ;;
    esac
done
