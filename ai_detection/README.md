# AI 违规检测模块

TrafficMind 交通智脑 - AI 违规检测模块

## 📁 目录结构

```
ai_detection/
├── violation_detector.py      # 核心违规检测器
├── backend_api_client.py      # 后端API客户端
├── vehicle_tracker.py         # 车辆追踪器
├── main_pipeline.py           # 主处理流程
├── main_pipeline_manual.py    # 手动测试流程
├── signal_adapter.py          # 信号灯适配器
├── manual_signal_controller.py # 手动信号控制器
├── test_backend_integration.py # 后端集成测试
├── requirements.txt           # Python依赖
├── yolov8n.pt                 # YOLOv8 Nano模型
├── yolov8s.pt                 # YOLOv8 Small模型
├── data/                      # 数据文件
│   ├── rois.json              # ROI配置
│   └── *.mp4                  # 测试视频
├── doc/                       # 文档
├── Utility/                   # 工具脚本
│   ├── roi_labeler.py         # ROI标注工具
│   ├── roi_visualizer.py      # ROI可视化工具
│   └── video_rotator.py       # 视频旋转工具
└── violations/                # 违规截图输出
```

## 🚀 快速开始

### 1. 安装依赖

```bash
cd ai_detection
pip install -r requirements.txt
```

### 2. 确保后端服务运行

后端服务需要运行在 `http://localhost:8081`

### 3. 运行测试

```bash
# 测试后端连接
python test_backend_integration.py

# 测试违规检测器
python violation_detector.py
```

### 4. 运行完整检测流程

```bash
python main_pipeline.py
```

## 🔧 功能说明

### 违规检测类型

| 类型 | 说明 | API类型 |
|------|------|---------|
| 闯红灯 | red_light_running | RED_LIGHT |
| 逆行 | wrong_way_driving | WRONG_WAY |
| 跨实线变道 | lane_change_across_solid_line | CROSS_SOLID_LINE |
| 待转区违规 | waiting_area_* | ILLEGAL_TURN |

### API 集成

违规检测器会自动将检测到的违规上报到后端：

```python
detector = ViolationDetector(
    rois_path="./data/rois.json",
    screenshot_dir="./violations",
    intersection_id=1,      # 路口ID
    enable_api=True         # 启用API上报
)
```

## 📝 注意事项

1. 后端服务端口：8081
2. 需要 Docker 运行数据库服务（MySQL、Redis、MinIO）
3. YOLO 模型文件较大，首次运行可能需要下载

