# AI 违规检测模块 - Python 文件说明

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
# 服务运行在 http://localhost:5000
```

**方式二：启动实时检测服务（视频流 + WebSocket）**
```bash
cd ai_detection/api
python ai_realtime_service.py
# HTTP: http://localhost:5000
# WebSocket: ws://localhost:5000
```

### 3. 测试图片检测

```bash
# 检测单张图片
python scripts/test_image.py --image ./data/car_1_red.png

# 批量检测
python scripts/test_image.py --folder ./data

# 指定信号灯状态
python scripts/test_image.py --image ./data/car_1_red.png     --signals north_bound=red,south_bound=green,west_bound=green,east_bound=red
```

---

## API 服务

### detection_api.py - 图片检测 API

端口: http://localhost:5000

| 端点 | 方法 | 说明 |
|------|------|------|
| /health | GET | 健康检查 |
| /detect-image | POST | 检测图片（multipart/form-data） |
| /detect-image-base64 | POST | 检测 Base64 图片 |

调用示例:
```python
import requests

# 检测图片
response = requests.post('http://localhost:5000/detect-image',
    files={'image': open('test.jpg', 'rb')},
    data={'signals': '{"north_bound": "red", "south_bound": "green"}'}
)
result = response.json()
```

### ai_realtime_service.py - 实时检测服务

端口: http://localhost:5000

HTTP 端点:
| 端点 | 方法 | 说明 |
|------|------|------|
| /health | GET | 健康检查 |
| /start-realtime | POST | 启动实时处理任务 |
| /test-local | POST | 本地视频测试 |
| /api/traffic | POST | 接收信号灯数据 |
| /api/traffic/status | GET | 获取信号灯状态 |
| /detect-image | POST | 图片检测 |
| /detect-image-base64 | POST | Base64 图片检测 |

WebSocket 事件:
| 事件 | 方向 | 说明 |
|------|------|------|
| frame | 服务端→客户端 | 实时处理帧（Base64 JPEG） |
| violation | 服务端→客户端 | 违规检测事件 |
| complete | 服务端→客户端 | 处理完成通知 |
| signal_update | 服务端→客户端 | 信号灯状态更新 |

---

## 核心模块

### violation_detector.py

功能: 视频流违规检测器（需要车辆追踪轨迹）

检测类型:
| 类型 | 内部名称 | API 类型 |
|------|---------|---------|
| 闯红灯 | red_light_running | RED_LIGHT |
| 逆行 | wrong_way_driving | WRONG_WAY |
| 跨实线变道 | lane_change_across_solid_line | CROSS_SOLID_LINE |
| 待转区违规 | waiting_area_* | ILLEGAL_TURN |

使用:
```python
from core.violation_detector import ViolationDetector

detector = ViolationDetector(
    rois_path="./data/rois.json",
    screenshot_dir="./output/screenshots",
    intersection_id=1,
    enable_api=True
)

# 处理视频帧（需要先追踪车辆）
violations = detector.process_frame(frame, tracks, timestamp_ms)
```

### image_violation_detector.py

功能: 图片违规检测器（单帧检测，无需轨迹）

检测类型:
| 类型 | 说明 |
|------|------|
| 闯红灯 | 检测车辆是否在红灯状态下位于停止线内 |
| 跨实线变道 | 检测车辆中心点到实线的距离 |

使用:
```python
from core.image_violation_detector import ImageViolationDetector

detector = ImageViolationDetector(
    rois_path="./data/rois.json",
    model_path="yolov8s.pt",
    screenshot_dir="./output/screenshots",
    enable_api=False
)

# 检测图片
result = detector.process_image(
    image_path="test.jpg",
    signal_states={"north_bound": "red"},
    detect_types=["red_light", "lane_change"]
)
```

### vehicle_tracker.py

功能: 车辆检测与追踪（YOLOv8 + DeepSORT）

使用:
```python
from core.vehicle_tracker import VehicleTracker

tracker = VehicleTracker(
    model_path="yolov8s.pt",
    conf_threshold=0.25
)

# 检测并追踪
tracks = tracker.detect_and_track(frame)
# 返回: [(track_id, (x1, y1, x2, y2)), ...]

# 绘制检测结果
annotated = tracker.draw_detections(frame, tracks)
```

---

## 工具脚本

### signal_adapter.py

功能: 信号灯数据格式转换（后端格式 → 系统格式）

信号代码:
| 代码 | 含义 | 绿灯方向 |
|------|------|---------|
| ETWT | 东西直行 | east_bound, west_bound |
| NTST | 南北直行 | north_bound, south_bound |
| ELWL | 东西左转 | east_bound, west_bound |
| NLSL | 南北左转 | north_bound, south_bound |

使用:
```python
from tools.signal_adapter import SignalAdapter

# 格式转换
backend_data = [
    {"路口": 0, "信号": "ETWT", "排队车辆": 4},
]
signal_states = SignalAdapter.convert_backend_to_system(backend_data)
# 返回: {'north_bound': 'red', 'south_bound': 'red', 'east_bound': 'green', 'west_bound': 'green'}
```

---

## 输出目录

```
output/
├── videos/        # 处理后的视频文件 (*.mp4)
│   └── *_result.mp4
├── screenshots/   # 违规车辆截图 (*.jpg)
│   ├── RED_*.jpg  # 闯红灯截图
│   └── LANE_*.jpg # 压线截图
└── reports/       # 违规记录 JSON
    └── *_violations.json
```

---

## 文件调用关系

```
api/detection_api.py
    └── core/image_violation_detector.py
            └── tools/signal_adapter.py

api/ai_realtime_service.py (主服务)
    ├── core/violation_detector.py
    │   └── api/backend_api_client.py
    ├── core/vehicle_tracker.py
    └── tools/signal_adapter.py

scripts/test_image.py (常用测试)
    └── core/image_violation_detector.py

scripts/main_pipeline.py
    ├── core/violation_detector.py
    └── core/vehicle_tracker.py

scripts/manual_signal_controller.py
    ├── core/violation_detector.py
    ├── core/vehicle_tracker.py
    └── scripts/manual_signal_controller.py
```

---

## 注意事项

1. 后端服务: Java 后端需运行在 http://localhost:8081
2. 数据库: 需要 Docker 运行 MySQL、Redis、MinIO
3. 模型文件: yolov8s.pt 需从 Ultralytics 下载
4. 临时文件: temp_videos/ 目录用于临时视频存储

---

## 依赖安装

```bash
pip install opencv-python ultralytics flask flask-cors flask-socketio
pip install requests numpy eventlet
```
