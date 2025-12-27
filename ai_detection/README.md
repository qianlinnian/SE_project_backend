# AI 检测模块 - Python 文件说明

> TrafficMind 交通智脑 - AI 违规检测模块
>
> 更新日期: 2025-12-24

---

## 目录结构

```
ai_detection/
├── api/                        # API 服务层
│   ├── ai_realtime_service.py  # 实时检测服务（WebSocket + HTTP）主服务
│   ├── detection_api.py        # 图片检测 API（Flask）
│   └── backend_api_client.py   # 后端 API 客户端
│
├── core/                       # 核心检测模块
│   ├── violation_detector.py   # 视频流违规检测器（需轨迹）
│   ├── image_violation_detector.py  # 图片违规检测器（单帧）
│   └── vehicle_tracker.py      # 车辆追踪器（YOLOv8 + DeepSORT）
│
├── scripts/                    # 测试脚本
│   ├── test_image.py           # 图片检测测试 常用
│   ├── test_realtime_service.py    # 实时服务测试
│   ├── test_flask_api.py           # Flask API 测试
│   ├── test_backend_integration.py # 后端集成测试
│   ├── test_yolo_simple.py         # YOLO 简单测试
│   ├── visualize_detection.py      # 检测结果可视化
│   ├── main_pipeline.py            # 主流程（自动信号灯）
│   └── manual_signal_controller.py # 手动信号灯控制
│
├── tools/                      # 工具脚本
│   ├── signal_adapter.py       # 信号灯格式适配器
│   ├── roi_labeler.py          # ROI 标注工具
│   ├── roi_visualizer.py       # ROI 可视化工具
│   └── video_rotator.py        # 视频旋转工具
│
├── data/                       # 输入数据
│   ├── rois.json               # ROI 区域配置
│   └── *.mp4                   # 测试视频
│
├── output/                     # 输出目录
│   ├── videos/                 # 处理后的视频
│   ├── screenshots/            # 违规截图
│   └── reports/                # 违规记录 JSON
│
├── requirements.txt            # Python 依赖
└── yolov8s.pt                  # YOLOv8 模型（需下载）
```

---

## 快速开始

### 1. 环境准备

```bash
# 进入 AI 检测目录
cd d:\course_content\SE\seprojects\SE_project_backend\ai_detection

# 激活 Python 环境
conda activate yolov8

# 安装依赖（首次运行）
pip install -r requirements.txt
```

---

## 🚀 启动服务

### 【后端】AI 检测服务

#### 方式 1: 图片检测 API（Flask）

```bash
# 进入 API 目录
cd api

# 启动服务（端口: 5000）
python detection_api.py
```

**用途**: 单张图片违规检测
**端点**: POST /detect-image, POST /detect-image-base64

---

#### 方式 2: 实时检测服务（主服务，推荐）

```bash
# 进入 API 目录
cd api

# 启动服务（端口: 5000）
python ai_realtime_service.py
```

**用途**: 视频流实时检测 + WebSocket 推送 + 图片检测
**特性**:
- ✅ WebSocket 实时推送检测帧
- ✅ 支持图片和视频检测
- ✅ 违规事件实时通知

**健康检查**:
```bash
curl http://localhost:5000/health
```

---

### 【前端】React 应用

#### HTML 演示版（最快）

```bash
# 直接打开 HTML 文件（无需安装依赖）
cd frontend-examples
# 双击 demo.html 即可在浏览器打开
```

---

#### Vite + React 开发版（完整功能）

```bash
# 进入前端目录
cd frontend-examples/vite-app

# 安装依赖（首次运行）
npm install

# 启动开发服务器（端口: 3000）
npm run dev
```

**访问地址**: http://localhost:3000

---

## 🧪 测试命令

### 测试图片检测

**单张图片**:
```bash
python scripts/test_image.py --image violations_images/car_1_cross.png
```

**批量检测文件夹**:
```bash
python scripts/test_image.py --folder violations_images --debug
```

**指定信号灯状态**:
```bash
python scripts/test_image.py --image data/car_1_red.png --signals north_bound=red,south_bound=green
```

**导出违规记录**:
```bash
python scripts/test_image.py --folder violations_images --export output/violations.json
```

---

### 测试视频检测

**基础视频检测**:
```bash
python scripts/main_pipeline_manual.py --video data/test_video.mp4 --output result.mp4

python scripts/main_pipeline_manual.py --video data/car_2_cross_wrong_n.mp4 --output result.mp4

```

**旋转视频（如果视频方向不对）**:
```bash
python scripts/main_pipeline_manual.py --video data/test.mp4 --output result.mp4 --rotation 90
```

**不显示可视化窗口**:
```bash
python scripts/main_pipeline_manual.py --video data/test.mp4 --output result.mp4 --no-display
```

---

### 测试 WebSocket 实时服务

```bash
# 1. 先启动 AI 服务
cd api
python ai_realtime_service.py

# 2. 新开终端，运行测试脚本
cd ..
python scripts/test_realtime_service.py
```

**测试内容**:
- ✅ 健康检查
- ✅ WebSocket 连接
- ✅ 实时帧接收
- ✅ 违规事件推送

---

### 测试 YOLO 检测

```bash
# 简单测试 YOLO 是否正常工作
python scripts/test_yolo_simple.py
```

**输出**: 显示不同置信度阈值下的检测结果

---

### 可视化 ROI 区域

```bash
python scripts/visualize_detection.py
```

**输出**: `data/detection_visualization.jpg`（显示停止线、车辆检测框）

---

## ⚙️ 完整启动流程（前后端联调）

### 第 1 步: 启动后端服务

**终端 1 - AI 检测服务**:
```bash
cd d:\course_content\SE\seprojects\SE_project_backend\ai_detection\api
conda activate yolov8
python ai_realtime_service.py
```

**终端 2 - Java 后端（可选，用于数据持久化）**:
```bash
cd d:\course_content\SE\seprojects\SE_project_backend
# 启动 Java 后端（端口: 8081）
java -jar backend.jar
```

---

### 第 2 步: 启动前端

**终端 3 - React 前端**:
```bash
cd d:\course_content\SE\seprojects\SE_project_backend\ai_detection\frontend-examples\vite-app
npm run dev
```

**访问**: http://localhost:3000

---

### 第 3 步: 测试流程

1. 打开浏览器访问 http://localhost:3000
2. 上传图片或视频进行检测
3. 查看实时检测结果和违规记录

---

## 🔍 常见问题排查

### 问题 1: ModuleNotFoundError: No module named 'core'

**原因**: 脚本从子目录运行，找不到父目录的模块
**解决**: 已在脚本中添加路径解析代码，直接运行即可

### 问题 2: 后端 API 连接失败（port 8081）

**现象**: 日志显示 `ConnectionError: HTTPConnectionPool(host='localhost', port=8081)`
**影响**: 违规记录不会上报到后端数据库，仅本地保存
**解决**: 如需数据持久化，请启动 Java 后端服务

### 问题 3: YOLOv8 模型未找到

**现象**: `FileNotFoundError: yolov8s.pt`
**解决**:
```bash
# 自动下载模型
python -c "from ultralytics import YOLO; YOLO('yolov8s.pt')"
```

### 问题 4: OpenCV 无法显示窗口

**现象**: 视频检测时无法显示可视化窗口
**解决**: 使用 `--no-display` 参数跳过可视化
```bash
python scripts/main_pipeline_manual.py --video data/test.mp4 --no-display
```

---

## API 服务

### detection_api.py - 图片检测 API

端口: http://localhost:5000

| 端点 | 方法 | 说明 |
|------|------|------|
| /health | GET | 健康检查 |
| /detect-image | POST | 检测图片 |
| /detect-image-base64 | POST | 检测 Base64 图片 |

### ai_realtime_service.py - 实时检测服务

端口: http://localhost:5000

HTTP 端点:
| 端点 | 方法 | 说明 |
|------|------|------|
| /health | GET | 健康检查 |
| /start-realtime | POST | 启动实时处理任务 |
| /test-local | POST | 本地视频测试 |
| /api/traffic | POST | 接收信号灯数据 |
| /detect-image | POST | 图片检测 |

WebSocket 事件:
| 事件 | 说明 |
|------|------|
| frame | 实时处理帧 |
| violation | 违规检测事件 |
| complete | 处理完成通知 |

---

## 核心模块

### violation_detector.py

视频流违规检测器（需要车辆追踪轨迹）

检测类型:
- 闯红灯 (red_light_running)
- 逆行 (wrong_way_driving)
- 跨实线变道 (lane_change_across_solid_line)

### image_violation_detector.py

图片违规检测器（单帧检测，无需轨迹）

检测类型:
- 闯红灯
- 跨实线变道

### vehicle_tracker.py

车辆检测与追踪（YOLOv8 + DeepSORT）

---

## 工具脚本

### signal_adapter.py

信号灯数据格式转换（后端格式 → 系统格式）

信号代码:
- ETWT = 东西直行
- NTST = 南北直行
- ELWL = 东西左转
- NLSL = 南北左转

---

## 输出目录

```
output/
├── videos/        # 处理后的视频 (*.mp4)
├── screenshots/   # 违规截图 (*.jpg)
└── reports/       # 违规记录 JSON
```

---

## 文件调用关系

```
api/detection_api.py
    └─> core/image_violation_detector.py

api/ai_realtime_service.py (主服务)
    ├─> core/violation_detector.py
    │   └─> api/backend_api_client.py
    ├─> core/vehicle_tracker.py
    └─> tools/signal_adapter.py

scripts/test_image.py
    └─> core/image_violation_detector.py

scripts/main_pipeline.py
    ├─> core/violation_detector.py
    └─> core/vehicle_tracker.py
```

---

## 注意事项

1. 后端服务: Java 后端需运行在 http://localhost:8081
2. 数据库: 需要 Docker 运行 MySQL、Redis、MinIO
3. 模型文件: yolov8s.pt 需从 Ultralytics 下载



