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

### 1. 安装依赖

```bash
cd ai_detection
conda activate yolov8
pip install -r requirements.txt
```

### 2. 启动服务

**方式一：启动 Flask API（图片检测）**
```bash
cd ai_detection/api
python detection_api.py
```

**方式二：启动实时检测服务（视频流 + WebSocket）**
```bash
cd ai_detection/api
python ai_realtime_service.py
```

### 3. 测试图片检测

```bash
python scripts/test_image.py --image ./data/car_1_red.png
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


