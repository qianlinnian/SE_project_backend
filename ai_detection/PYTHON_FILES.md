# AI 检测模块 - Python 文件汇总

> TrafficMind 智慧交通管理系统 - AI 违规检测模块
> 
> 更新日期: 2025-12-23

---

## 📁 目录结构

```
ai_detection/
├── 🚀 核心服务
│   └── ai_realtime_service.py    # 实时检测服务（WebSocket）⭐ 主服务
│
├── 🔧 核心模块
│   ├── violation_detector.py     # 违规检测器
│   ├── vehicle_tracker.py        # 车辆追踪器（YOLOv8）
│   ├── backend_api_client.py     # 后端 API 客户端
│   └── signal_adapter.py         # 信号灯适配器
│
├── 🎮 运行脚本
│   ├── main_pipeline.py          # 主流程（自动信号灯）
│   ├── main_pipeline_manual.py   # 主流程（手动信号灯）
│   └── manual_signal_controller.py # 手动信号灯控制器
│
├── 🧪 测试脚本
│   ├── test_realtime_service.py  # 实时服务测试
│   └── test_backend_integration.py # 后端集成测试
│
├── 🛠️ 工具脚本
│   └── Utility/
│       ├── roi_labeler.py        # ROI 标注工具
│       ├── roi_visualizer.py     # ROI 可视化工具
│       └── video_rotator.py      # 视频旋转工具
│
├── 📦 配置文件
│   ├── requirements.txt          # Python 依赖
│   └── data/rois.json            # ROI 配置
│
└── 📂 输出目录
    ├── violations/               # 违规截图
    ├── output_videos/            # 处理后的视频
    └── temp_videos/              # 临时视频文件
```

---

## 🚀 核心服务

### 1. ai_realtime_service.py ⭐

**功能**: AI 实时检测服务，支持 WebSocket 实时推流

**端口**: `http://localhost:5000`

**API 端点**:
| 端点 | 方法 | 说明 |
|------|------|------|
| `/health` | GET | 健康检查 |
| `/start-realtime` | POST | 启动实时处理任务 |
| `/test-local` | POST | 本地视频测试 |
| `/api/traffic` | POST | 接收信号灯数据 |
| `/api/traffic/status` | GET | 获取当前信号灯状态 |
| `/task/<id>` | GET | 查询任务状态 |

**WebSocket 事件**:
| 事件 | 方向 | 说明 |
|------|------|------|
| `frame` | 服务端→客户端 | 实时处理帧（Base64 JPEG） |
| `violation` | 服务端→客户端 | 违规检测事件 |
| `complete` | 服务端→客户端 | 处理完成通知 |
| `signal_update` | 服务端→客户端 | 信号灯状态更新 |
| `error` | 服务端→客户端 | 错误通知 |

**启动命令**:
```bash
conda activate yolov8
python ai_realtime_service.py
```

---

## 🔧 核心模块

### 3. violation_detector.py

**功能**: 违规检测器，核心检测逻辑

**检测类型**:
| 类型 | 内部名称 | API 名称 |
|------|---------|---------|
| 闯红灯 | `red_light_running` | `RED_LIGHT` |
| 逆行 | `wrong_way_driving` | `WRONG_WAY` |
| 跨实线变道 | `lane_change_across_solid_line` | `CROSS_SOLID_LINE` |
| 待转区违规 | `waiting_area_*` | `ILLEGAL_TURN` |

**主要方法**:
```python
detector = ViolationDetector(
    rois_path="./data/rois.json",
    screenshot_dir="./violations",
    intersection_id=1,
    enable_api=True
)

# 处理一帧
violations = detector.process_frame(frame, detections, timestamp)

# 更新信号灯状态
detector.update_signal_state('north_bound', 'red')
detector.update_left_turn_signal('north_bound', 'green')

# 获取统计
summary = detector.get_violation_summary()
```

---

### 4. vehicle_tracker.py

**功能**: 车辆检测与追踪（基于 YOLOv8）

**主要类**:
- `VehicleTracker` - 车辆追踪器
- `SimpleTrafficLightDetector` - 简单信号灯模拟器

**主要方法**:
```python
tracker = VehicleTracker(
    model_path="yolov8s.pt",
    conf_threshold=0.25
)

# 检测并追踪
detections = tracker.detect_and_track(frame)
# 返回: [(track_id, (x1, y1, x2, y2)), ...]

# 绘制检测结果
annotated_frame = tracker.draw_detections(frame, detections)
```

---

### 5. backend_api_client.py

**功能**: 后端 API 客户端，与 Java 后端通信

**主要方法**:
```python
client = BackendAPIClient("http://localhost:8081/api")

# 健康检查
is_healthy = client.health_check()

# 上报违规
violation_id = client.report_violation({
    'intersectionId': 1,
    'direction': 'SOUTH',
    'turnType': 'STRAIGHT',
    'plateNumber': '京A12345',
    'violationType': 'RED_LIGHT',
    'imageUrl': 'http://...',
    'aiConfidence': 0.95,
    'occurredAt': '2025-12-23T21:00:00'
})

# 获取信号灯状态
status = client.get_signal_status(1, 'SOUTH', 'STRAIGHT')

# 获取路口整体状态
all_status = client.get_intersection_status(1)
```

---

### 6. signal_adapter.py

**功能**: 信号灯数据格式转换

**信号代码**:
| 代码 | 含义 | 绿灯方向 |
|------|------|---------|
| ETWT | 东西直行 | east_bound, west_bound |
| NTST | 南北直行 | north_bound, south_bound |
| ELWL | 东西左转 | east_bound, west_bound |
| NLSL | 南北左转 | north_bound, south_bound |

**主要方法**:
```python
from signal_adapter import SignalAdapter

# 格式1: JSON 列表
backend_data = [
    {"路口": 0, "信号": "ETWT", "排队车辆": 4},
    {"路口": 1, "信号": "NTST", "排队车辆": 0},
]
signal_states = SignalAdapter.convert_backend_to_system(backend_data)
# 返回: {'north_bound': 'green', 'south_bound': 'green', ...}

# 格式2: 文本格式
text = "路口0: 信号=ETWT, 排队车辆=4\n路口1: 信号=NTST, 排队车辆=0"
signal_states = SignalAdapter.convert_backend_string_format(text)
```

---

## 🎮 运行脚本

### 7. main_pipeline.py

**功能**: 完整的交通违规检测管道（自动信号灯模拟）

**使用场景**: 本地视频测试，信号灯自动循环切换

```bash
python main_pipeline.py --video ./data/car_1_cross.mp4 --model yolov8s.pt
```

---

### 8. main_pipeline_manual.py

**功能**: 完整的交通违规检测管道（手动信号灯控制）

**使用场景**: 自制测试视频，需要手动控制信号灯

**键盘控制**:
- `1-4` - 切换直行信号灯（全红/全绿/南北绿/东西绿）
- `5-6` - 切换左转信号灯
- `N/S/W/E` - 单独切换某方向
- `Q` - 退出

```bash
python main_pipeline_manual.py --video ./data/car_1_cross.mp4
```

---

### 9. manual_signal_controller.py

**功能**: 手动信号灯控制器

**使用场景**: 被 main_pipeline_manual.py 调用

---

## 🧪 测试脚本

### 10. test_realtime_service.py

**功能**: 测试 AI 实时服务

```bash
# 先启动服务
python ai_realtime_service.py

# 另开终端测试
python test_realtime_service.py
```

---

### 11. test_backend_integration.py

**功能**: 测试与 Java 后端的集成

```bash
# 确保 Java 后端运行在 8081 端口
python test_backend_integration.py
```

---

## 🛠️ 工具脚本

### 12. Utility/roi_labeler.py

**功能**: ROI 区域标注工具

**用途**: 为新的视频/摄像头创建 ROI 配置

---

### 13. Utility/roi_visualizer.py

**功能**: ROI 可视化工具

**用途**: 查看和验证 ROI 配置是否正确

---

### 14. Utility/video_rotator.py

**功能**: 视频旋转工具

**用途**: 旋转视频角度

---

## 📦 依赖安装

```bash
conda activate yolov8
pip install -r requirements.txt
```

**主要依赖**:
- `opencv-python` - 图像处理
- `ultralytics` - YOLOv8
- `flask` - HTTP 服务
- `flask-socketio` - WebSocket
- `requests` - HTTP 客户端

---

## 🚀 快速开始

### 启动实时检测服务

```bash
# 1. 激活环境
conda activate yolov8

# 2. 进入目录
cd SE_project_backend/ai_detection

# 3. 启动服务
python ai_realtime_service.py
```

### 测试服务

```bash
# 新开终端
python test_realtime_service.py
```

---

## 📊 文件依赖关系

```
ai_realtime_service.py
├── violation_detector.py
│   └── backend_api_client.py
├── vehicle_tracker.py
└── signal_adapter.py

main_pipeline.py
├── violation_detector.py
└── vehicle_tracker.py

main_pipeline_manual.py
├── violation_detector.py
├── vehicle_tracker.py
└── manual_signal_controller.py
```

---

## 📝 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 2.0.0 | 2025-12-23 | 新增 WebSocket 实时推流、信号灯接口 |
| 1.0.0 | 2025-12-22 | 初始版本，基础检测功能 |

