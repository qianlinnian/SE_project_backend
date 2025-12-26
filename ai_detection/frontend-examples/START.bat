@echo off
chcp 65001 >nul
echo ========================================
echo    AI 交通检测 - 前端启动向导
echo ========================================
echo.

:menu
echo 请选择启动方式:
echo.
echo  [1] 打开 HTML 演示 (最简单)
echo  [2] 启动 Vite + React 开发服务器 (推荐)
echo  [3] 查看启动文档
echo  [0] 退出
echo.
set /p choice="请输入选项 (0-3): "

if "%choice%"=="1" goto html
if "%choice%"=="2" goto vite
if "%choice%"=="3" goto docs
if "%choice%"=="0" goto end

echo 无效选项，请重新选择
echo.
goto menu

:html
echo.
echo 正在打开 HTML 演示...
start demo.html
echo.
echo ✅ 已在浏览器中打开 demo.html
echo 💡 请确保后端已启动: python ai_detection/api/detection_api.py
echo.
pause
goto end

:vite
echo.
echo 正在启动 Vite 开发服务器...
echo.
cd vite-app

if not exist "node_modules" (
    echo ⚠️  未找到 node_modules，正在安装依赖...
    echo.
    npm install
    echo.
)

echo.
echo 🚀 启动开发服务器...
npm run dev

goto end

:docs
echo.
echo 正在打开启动文档...
start GETTING_STARTED.md
echo.
echo ✅ 已打开 GETTING_STARTED.md
echo.
pause
goto menu

:end
echo.
echo 感谢使用！👋
timeout /t 2 >nul
