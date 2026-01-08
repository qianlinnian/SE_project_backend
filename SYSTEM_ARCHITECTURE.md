# TrafficMind 交通智脑 - 系统架构文档

## 📋 目录
1. [系统概述](#系统概述)
2. [技术架构](#技术架构)
3. [核心模块](#核心模块)
   - [1. Python AI检测服务](#1-python-ai检测服务)
   - [1.5 LLM 信号控制服务](#15-llm-信号控制服务-llmlqy)
4. [API接口文档](#api接口文档)
5. [数据流向](#数据流向)
6. [调用关系](#调用关系)
7. [部署说明](#部署说明)

---

## 系统概述

### 项目信息
- **项目名称**: TrafficMind 交通智脑
- **团队**: Coders (冯俊财、关镜文、路清怡、黄弋涵)
- **核心功能**: 基于AI的智能交通违规检测与监控系统

### 核心功能
1. **AI车辆检测与追踪**
   - YOLOv8 实时车辆检测
   - DeepSORT 多目标追踪
   - 车辆轨迹分析

2. **智能违规检测**
   - 闯红灯检测 (Red Light Running)
   - 逆行检测 (Wrong-Way Driving)
   - 跨实线变道检测 (Cross Solid Line)
   - 违法转弯检测 (Illegal Turn)

3. **实时视频处理**
   - WebSocket 实时视频流传输
   - 逐帧违规分析
   - 实时信号灯同步

4. **图片违规检测**
   - 单张图片快速检测
   - 批量图片处理
   - 多种违规类型同时检测

5. **LLM 智能信号控制**
   - 基于大语言模型的交通信号优化
   - 实时交通流量分析
   - 自适应信号灯相位调整
   - 部署在 AutoDL 云端 GPU 服务器

6. **后端管理系统**
   - 违规记录管理
   - 用户认证与授权
   - 文件上传与存储
   - 实时数据统计

---

## 技术架构

### 技术栈

#### Python AI服务 (Flask)
- **框架**: Flask 3.x + Flask-SocketIO
- **AI模型**:
  - YOLOv8s (车辆检测)
  - Ultralytics (模型管理)
- **图像处理**: OpenCV 4.x
- **通信**:
  - WebSocket (实时通信)
  - HTTP REST API
- **并发**: Threading (异步处理)

#### Java后端服务 (Spring Boot)
- **框架**: Spring Boot 3.x
- **安全**: Spring Security + JWT
- **数据库**:
  - Spring Data JPA (ORM)
  - MySQL 8.x
- **缓存**: Spring Data Redis
- **验证**: Jakarta Validation
- **工具**: Lombok, RestTemplate

#### 前端 (React/Vue)
- **框架**: React 18 / Vue 3
- **语言**: TypeScript
- **通信**: Fetch API + WebSocket
- **UI**: Tailwind CSS / Ant Design

#### LLM 信号控制服务 (AutoDL 云端)
- **模型**: Qwen2.5-3B (通义千问)
- **框架**: PyTorch + Transformers
- **部署**: AutoDL GPU 云服务器
- **通信**: HTTP API (HTTPS)
- **功能**:
  - 交通流量实时分析
  - 基于 LLM 的信号相位决策
  - 自适应信号灯时长优化
- **地址**: `https://u836978-a67f-943bbb9f.westc.gpuhub.com:8443`

> **注意**: LLM 服务部署在 AutoDL 云端 GPU 服务器，与主系统分离部署，通过 HTTPS API 进行通信。

---

## 核心模块

### 1. Python AI检测服务

#### 1.1 车辆检测与追踪 (`vehicle_tracker.py`)

**功能**: 使用YOLOv8进行车辆检测和追踪

**核心类**: `VehicleTracker`

**主要方法**:
```python
class VehicleTracker:
    def __init__(model_path, conf_threshold=0.25, iou_threshold=0.45)
    def detect_and_track(frame) -> List[Tuple[track_id, bbox]]
    def draw_detections(frame, detections) -> frame
```

**调用流程**:
```
视频帧 → YOLOv8检测 → 内置追踪器 → 返回(track_id, bbox)列表
```

**特点**:
- 使用YOLOv8内置追踪功能 (persist=True)
- 只检测车辆类别: car(2), motorcycle(3), bus(5), truck(7)
- 保留30帧轨迹历史

---

#### 1.2 视频违规检测器 (`violation_detector.py`)

**功能**: 分析车辆轨迹，检测交通违规行为

**核心类**: `ViolationDetector`

**主要方法**:
```python
class ViolationDetector:
    def __init__(rois_path, screenshot_dir, intersection_id, enable_api)

    # 违规检测方法
    def detect_red_light_violation(detections, frame, timestamp) -> List[violation]
    def detect_wrong_way_violation(detections, frame, timestamp) -> List[violation]
    def detect_lane_change_violation(detections, frame, timestamp) -> List[violation]

    # 轨迹分析
    def _update_trajectory(track_id, center, timestamp)
    def _check_stop_line_crossing(track_id, position, direction)

    # 违规上报
    def _report_to_backend(violation_record, frame)  # 异步上报
    def _do_backend_report(violation_copy)  # 后台线程执行
```

**检测逻辑**:

**闯红灯检测**:
```
1. 判断信号灯状态为红灯
2. 检测车辆是否从停止线前方进入停止线区域
3. 使用状态机避免重复记录:
   - before: 车辆在停止线前
   - crossed: 车辆已穿越停止线
4. 冷却期: 10秒内不重复记录同一车辆相同违规
```

**逆行检测**:
```
1. 分析车辆轨迹方向
2. 与ROI定义的正常行驶方向比对
3. 方向相反 → 逆行违规
```

**跨实线变道检测**:
```
1. 记录车辆相对实线的位置（左侧/右侧）
2. 检测车辆从一侧穿越到另一侧
3. 状态变化: side从-1变为1 或 从1变为-1 → 压线违规
```

**异步上报机制**:
```python
# 违规检测到后立即创建后台线程上报，不阻塞主检测流程
threading.Thread(target=self._do_backend_report, args=(violation_copy,), daemon=True).start()

# 后台线程执行:
1. 上传违规截图到Java后端 (JWT认证)
2. 调用违规上报API
3. 记录上报结果
```

---

#### 1.3 图片违规检测器 (`image_violation_detector.py`)

**功能**: 单张图片快速违规检测（无需轨迹追踪）

**核心类**: `ImageViolationDetector`

**主要方法**:
```python
class ImageViolationDetector:
    def detect_vehicles(image, conf_threshold=0.15) -> List[(bbox, confidence)]
    def detect_red_light_violation(image, signal_states) -> List[violation]
    def detect_lane_change_violation(image) -> List[violation]
    def process_image(image_path, signal_states, detect_types) -> dict
    def process_image_data(image_array, signal_states) -> dict  # API专用
```

**与视频检测的区别**:
- 无追踪: 直接检测当前帧
- 快速: 适合单张图片分析
- 车头定位: 根据方向计算车头位置（20%车身长度）

---

#### 1.4 实时检测服务 (`ai_realtime_service.py`)

**功能**: Flask WebSocket服务，提供实时视频检测

**核心功能**:
- WebSocket视频流接收与处理
- 实时违规检测与推送
- 信号灯状态同步
- JWT认证与文件上传

**主要端点**:

| 端点 | 方法 | 功能 | 参数 |
|------|------|------|------|
| `/health` | GET | 健康检查 | - |
| `/detect-image` | POST | 单张图片检测 | `image` (file), `signals` (json), `detect_types` |
| `/detect-image-base64` | POST | Base64图片检测 | `image` (base64), `signals`, `detect_types` |
| `/detect-batch` | POST | 批量图片检测 | `images` (files), `signals`, `detect_types` |

**WebSocket事件**:

| 事件 | 方向 | 数据格式 | 说明 |
|------|------|----------|------|
| `start_detection` | Client→Server | `{videoData: base64}` | 开始检测 |
| `video_frame` | Client→Server | `{frame: base64, timestamp: ms}` | 发送视频帧 |
| `stop_detection` | Client→Server | `{}` | 停止检测 |
| `detection_result` | Server→Client | `{frame: base64, violations: [...]}` | 检测结果 |
| `traffic` | Server→Client | `{signals: {...}, leftTurnSignals: {...}}` | 信号灯状态 |
| `violation_alert` | Server→Client | `{violation: {...}}` | 违规警报 |
| `error` | Server→Client | `{message: string}` | 错误信息 |

**信号灯同步机制**:
```python
# 每2秒从Java后端拉取信号灯状态
def sync_signal_from_backend():
    response = requests.get(f"{BACKEND_API}/traffic/signals/realtime?intersectionId={INTERSECTION_ID}")
    if response.ok:
        data = response.json()
        # 更新直行信号
        current_signal_states = data.get('signals', {})
        # 更新左转信号
        current_left_turn_signals = data.get('leftTurnSignals', {})
        # 推送到前端
        socketio.emit('traffic', {'signals': ..., 'leftTurnSignals': ...})
```

**信号源模式切换**:
```python
POST /api/traffic/signal-source-mode
{
    "mode": "backend" | "simulation"
}

- backend: 从Java后端获取实时信号
- simulation: 使用时间循环模拟（30秒周期）
```

---

#### 1.5 后端API客户端 (`backend_api_client.py`)

**功能**: 与Java后端通信的客户端

**核心类**: `BackendAPIClient`

**主要方法**:
```python
class BackendAPIClient:
    def _login() -> jwt_token  # 自动登录获取JWT
    def upload_image(image_path) -> image_url  # 上传违规截图
    def report_violation(violation_data) -> violation_id  # 上报违规
    def health_check() -> bool  # 健康检查
```

**JWT认证流程**:
```
1. 初始化时自动调用_login()
2. POST /api/auth/login {username, password}
3. 提取accessToken
4. 所有后续请求携带: Authorization: Bearer <token>
```

**上传图片流程**:
```python
1. 打开本地截图文件
2. 构造multipart/form-data请求
3. POST /api/files/upload
   - files: {'file': (filename, file_object, 'image/jpeg')}
   - data: {'type': 'violation'}
   - headers: {'Authorization': 'Bearer <token>'}
4. 返回: {'url': 'http://localhost:8081/api/files/download?filename=...'}
```

**上报违规流程**:
```python
1. 先上传截图获取image_url
2. POST /api/violations/report
   {
     "intersectionId": 1,
     "direction": "NORTH",
     "turnType": "STRAIGHT",
     "plateNumber": "UNIDENTIFIED_001",
     "violationType": "RED_LIGHT",
     "imageUrl": "http://...",
     "aiConfidence": 0.95,
     "occurredAt": "2025-12-27T10:30:00"
   }
3. 返回: {'id': 154, 'message': 'Violation reported successfully'}
```

---

### 1.5 LLM 信号控制服务 (`LLMlqy/`)

**功能**: 基于大语言模型的交通信号智能优化

**模块结构**:
```
LLMlqy/
├── run_open_LLM.py        # 主程序入口
├── utils/
│   ├── llm_inference.py   # LLM 推理模块
│   ├── config.py          # 配置文件
│   └── ...
├── data/                  # 交通数据集
│   ├── Hangzhou/          # 杭州数据集
│   ├── Jinan/             # 济南数据集
│   └── NewYork/           # 纽约数据集
├── prompts/               # Prompt 模板
│   └── prompt_commonsense.json
└── records/               # 运行记录
```

**核心类**: `LLM_Inference`

**主要方法**:
```python
class LLM_Inference:
    def __init__(model_path, new_max_tokens=512)
    def generate_signal_phase(traffic_data) -> str  # 生成信号相位决策
    def analyze_traffic_flow(flow_data) -> dict     # 分析交通流量
    def optimize_timing(current_phase, waiting) -> dict  # 优化信号时长
```

**LLM 信号决策流程**:
```
1. 收集交通数据
   ├── 各方向车辆排队长度
   ├── 当前信号灯状态
   └── 时间信息

2. 发送到 AutoDL LLM 服务
   └── HTTPS POST: https://u836978-a67f-943bbb9f.westc.gpuhub.com:8443

3. LLM 分析并生成决策
   └── 输出格式: "ETWT", "NSNL", "NTNL", ...

4. 解析并应用到信号灯
   └── 更新主服务器信号灯状态
```

**支持的相位编码**:
| 编码 | 含义 | 直行信号 | 左转信号 |
|------|------|----------|----------|
| NT | 北向直行 | 🟢 绿灯 | 🔴 红灯 |
| NL | 北向左转 | 🔴 红灯 | 🟢 绿灯 |
| ST | 南向直行 | 🟢 绿灯 | 🔴 红灯 |
| SL | 南向左转 | 🔴 红灯 | 🟢 绿灯 |
| ET | 东向直行 | 🟢 绿灯 | 🔴 红灯 |
| EL | 东向左转 | 🔴 红灯 | 🟢 绿灯 |
| WT | 西向直行 | 🟢 绿灯 | 🔴 红灯 |
| WL | 西向左转 | 🔴 红灯 | 🟢 绿灯 |

**示例**:
- `ETWT` → 东西直行绿灯，其他方向红灯
- `NSNL` → 南北直行+左转绿灯，其他方向红灯
- `NTNL` → 南北直行+左转绿灯（北向+南向）

**部署说明**:
- **位置**: AutoDL 云端 GPU 服务器
- **地址**: `https://u836978-a67f-943bbb9f.westc.gpuhub.com:8443`
- **模型**: Qwen2.5-3B (通义千问 30亿参数)
- **通信**: HTTPS API
- **数据流**: 主服务器 → HTTPS → AutoDL LLM → HTTPS → 主服务器

---

### 2. Java后端服务

#### 2.1 认证控制器 (`AuthController.java`)

**功能**: 用户登录与JWT token生成

**端点**:
```java
POST /api/auth/login
Request: {"username": "admin", "password": "password123"}
Response: {
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGc...",
    "user": {
      "id": 1,
      "username": "admin",
      "role": "ADMIN"
    }
  }
}
```

---

#### 2.2 违规管理控制器 (`ViolationController.java`)

**功能**: 违规记录的CRUD操作

**端点**:

| 端点 | 方法 | 功能 | 认证要求 |
|------|------|------|----------|
| `/api/violations/report` | POST | 上报违规 | JWT |
| `/api/violations` | GET | 查询违规列表 | JWT |
| `/api/violations/{id}` | GET | 查看违规详情 | JWT |
| `/api/violations/{id}/process` | PUT | 处理违规 | JWT + ROLE |
| `/api/violations/count` | GET | 违规总数 | JWT |

**上报违规接口**:
```java
POST /api/violations/report
Request: {
  "intersectionId": 1,
  "direction": "NORTH",
  "turnType": "STRAIGHT",
  "plateNumber": "粤A12345",
  "violationType": "RED_LIGHT",  // RED_LIGHT | WRONG_WAY | CROSS_SOLID_LINE | ILLEGAL_TURN
  "imageUrl": "http://localhost:8081/api/files/download?filename=...",
  "aiConfidence": 0.95,
  "occurredAt": "2025-12-27T10:30:00"
}

Response: {
  "id": 154,
  "message": "Violation reported successfully"
}
```

**数据库存储**:
```java
@Entity
@Table(name = "violations")
public class Violation {
    private Long id;
    private Long intersectionId;
    private Direction direction;  // EAST, SOUTH, WEST, NORTH
    private TurnType turnType;  // STRAIGHT, LEFT_TURN, RIGHT_TURN, U_TURN
    private String plateNumber;
    private ViolationType violationType;
    private String imageUrl;  // 违规截图URL
    private Float aiConfidence;
    private LocalDateTime occurredAt;
    private ViolationStatus status;  // PENDING, CONFIRMED, REJECTED
    private AppealStatus appealStatus;  // NO_APPEAL, APPEALING, APPEALED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

---

#### 2.3 文件管理控制器 (`FileController.java`)

**功能**: 图片上传与下载

**端点**:

| 端点 | 方法 | 功能 | 认证要求 |
|------|------|------|----------|
| `/api/files/upload` | POST | 上传文件 | JWT |
| `/api/files/download?filename=xxx` | GET | 下载文件 | 公开 |
| `/api/files/get-url` | POST | 获取文件URL | JWT |

**文件存储结构**:
```
./uploads/
  └── violation/
      └── 2025/
          └── 12/
              └── 27/
                  ├── abc123.jpg
                  ├── def456.jpg
                  └── ...
```

**上传接口**:
```java
POST /api/files/upload
Content-Type: multipart/form-data

Parameters:
  - file: (binary)
  - type: "violation" | "general"

Response: {
  "success": true,
  "filename": "abc123.jpg",
  "path": "violation/2025/12/27/abc123.jpg",
  "url": "http://localhost:8081/api/files/download?filename=violation/2025/12/27/abc123.jpg",
  "originalName": "screenshot.jpg",
  "size": 245678,
  "type": "violation"
}
```

**安全措施**:
- 路径遍历防护: 过滤 `..`, `/`, `\`
- 文件类型限制: 仅允许 jpg, jpeg, png, gif, bmp, webp
- 唯一文件名: UUID生成

---

#### 2.4 图片检测控制器 (`ImageDetectionController.java`)

**功能**: 调用Python AI服务进行图片检测（代理层）

**端点**:

| 端点 | 方法 | 功能 |
|------|------|------|
| `/api/image-detection/detect` | POST | 检测图片（multipart） |
| `/api/image-detection/detect-base64` | POST | 检测图片（base64） |
| `/api/image-detection/detect-batch` | POST | 批量检测 |
| `/api/image-detection/red-light` | POST | 闯红灯专用检测 |
| `/api/image-detection/lane-change` | POST | 压线专用检测 |
| `/api/image-detection/health` | GET | AI服务健康检查 |

**调用流程**:
```
前端 → Java Controller → Python Flask API → AI检测 → 返回结果 → Java自动上报违规 → 前端
```

**自动上报逻辑**:
```java
// 检测完成后自动调用违规上报
if (totalViolations > 0) {
    autoReportViolations(detectionResult);
}
```

---

#### 2.5 信号灯控制器 (`SignalController.java`)

**功能**: 信号灯配置管理

**端点**:

| 端点 | 方法 | 功能 | 权限 |
|------|------|------|------|
| `/api/signals/{id}/adjust` | POST | 调整信号灯配置 | ADMIN/POLICE |
| `/api/signals/{id}/config` | GET | 获取信号灯配置 | ADMIN/POLICE |
| `/api/signals` | GET | 获取所有信号灯配置 | ADMIN/POLICE |

---

#### 2.6 交通监控控制器 (`TrafficMonitorController.java`)

**功能**: 实时交通数据查询（从Redis读取）

**端点**:

| 端点 | 方法 | 功能 |
|------|------|------|
| `/api/intersections` | GET | 获取所有路口列表 |
| `/api/intersections/{id}/realtime` | GET | 获取路口实时数据 |
| `/api/dashboard/stats` | GET | 交通趋势分析报告 |
| `/api/dashboard/heatmap` | GET | 车辆分布热力图 |

---

#### 2.7 用户初始化服务 (`UserInitializationService.java`)

**功能**: 应用启动时创建默认用户

**默认用户**:
```java
1. admin / password123  (管理员)
2. police01 / police123  (交警)
3. ai-detection-service / ai_service_2025  (AI服务账号)
```

**AI服务账号说明**:
- 专用于Python AI服务的API调用
- 角色: ADMIN（确保有足够权限）
- 避免使用真实用户凭证

---

## API接口文档

### Python Flask API (Port 5000)

#### 健康检查
```http
GET /health
Response: {
  "status": "healthy",
  "service": "TrafficMind AI Detection Service",
  "version": "1.0.0"
}
```

#### 单张图片检测
```http
POST /detect-image
Content-Type: multipart/form-data

Parameters:
  - image: (file) 图片文件
  - signals: (string, optional) JSON格式信号灯状态
  - detect_types: (string, optional) "red_light,lane_change"

Response: {
  "success": true,
  "total_violations": 2,
  "red_light_violations": 1,
  "lane_change_violations": 1,
  "violations": [
    {
      "id": "RED_north_bound_0_1735276800",
      "type": "red_light_running",
      "direction": "north_bound",
      "confidence": 0.95,
      "timestamp": "2025-12-27T10:30:00",
      "screenshot": "./output/screenshots/RED_north_bound_0_1735276800.jpg",
      "backend_id": 154
    }
  ],
  "annotated_image": "base64..."
}
```

#### Base64图片检测
```http
POST /detect-image-base64
Content-Type: application/json

Request: {
  "image": "base64...",
  "signals": {"north_bound": "red", ...},
  "detect_types": "red_light,lane_change"
}

Response: (同上)
```

#### 批量图片检测
```http
POST /detect-batch
Content-Type: multipart/form-data

Parameters:
  - images: (files) 多个图片文件
  - signals: (string, optional)
  - detect_types: (string, optional)

Response: {
  "success": true,
  "total_images": 5,
  "total_violations": 8,
  "results": [...]
}
```

---

### Java Spring Boot API (Port 8081)

#### 用户认证

**登录**
```http
POST /api/auth/login
Content-Type: application/json

Request: {
  "username": "admin",
  "password": "password123"
}

Response: {
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "eyJhbGc...",
    "user": {
      "id": 1,
      "username": "admin",
      "fullName": "系统管理员",
      "role": "ADMIN"
    }
  }
}
```

#### 违规管理

**上报违规**
```http
POST /api/violations/report
Authorization: Bearer <token>
Content-Type: application/json

Request: {
  "intersectionId": 1,
  "direction": "NORTH",
  "turnType": "STRAIGHT",
  "plateNumber": "粤A12345",
  "violationType": "RED_LIGHT",
  "imageUrl": "http://localhost:8081/api/files/download?filename=...",
  "aiConfidence": 0.95,
  "occurredAt": "2025-12-27T10:30:00"
}

Response: {
  "id": 154,
  "message": "Violation reported successfully"
}
```

**查询违规列表**
```http
GET /api/violations?page=1&size=10
Authorization: Bearer <token>

Response: [
  {
    "id": 154,
    "intersectionId": 1,
    "direction": "NORTH",
    "plateNumber": "粤A12345",
    "violationType": "RED_LIGHT",
    "imageUrl": "http://...",
    "status": "PENDING",
    "occurredAt": "2025-12-27T10:30:00",
    "createdAt": "2025-12-27T10:30:05"
  }
]
```

**查看违规详情**
```http
GET /api/violations/{id}
Authorization: Bearer <token>

Response: {
  "id": 154,
  "intersectionId": 1,
  "direction": "NORTH",
  "turnType": "STRAIGHT",
  "plateNumber": "粤A12345",
  "violationType": "RED_LIGHT",
  "imageUrl": "http://...",
  "aiConfidence": 0.95,
  "status": "PENDING",
  "appealStatus": "NO_APPEAL",
  "occurredAt": "2025-12-27T10:30:00",
  "createdAt": "2025-12-27T10:30:05",
  "updatedAt": "2025-12-27T10:30:05"
}
```

#### 文件管理

**上传文件**
```http
POST /api/files/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data

Parameters:
  - file: (binary)
  - type: "violation"

Response: {
  "success": true,
  "filename": "abc123.jpg",
  "path": "violation/2025/12/27/abc123.jpg",
  "url": "http://localhost:8081/api/files/download?filename=violation/2025/12/27/abc123.jpg",
  "originalName": "screenshot.jpg",
  "size": 245678,
  "type": "violation"
}
```

**下载文件**
```http
GET /api/files/download?filename=violation/2025/12/27/abc123.jpg

Response: (binary image data)
Content-Type: image/jpeg
```

#### 信号灯管理

**获取实时信号灯状态**
```http
GET /api/traffic/signals/realtime?intersectionId=1

Response: {
  "signals": {
    "north_bound": "red",
    "south_bound": "red",
    "west_bound": "green",
    "east_bound": "green"
  },
  "leftTurnSignals": {
    "north_bound": "red",
    "south_bound": "red",
    "west_bound": "red",
    "east_bound": "red"
  }
}
```

**切换信号源模式**
```http
POST /api/traffic/signal-source-mode
Content-Type: application/json

Request: {
  "mode": "backend"  // "backend" | "simulation"
}

Response: {
  "success": true,
  "mode": "backend"
}
```

---

## 数据流向

### 1. 实时视频检测流程

```
┌─────────────┐
│   前端UI    │
│  (React)    │
└──────┬──────┘
       │ WebSocket连接
       ↓
┌──────────────────────────────────────────────┐
│  Flask WebSocket服务 (ai_realtime_service)   │
│  - 接收视频帧                                │
│  - 解码Base64                                │
└──────┬───────────────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────────────┐
│  VehicleTracker (vehicle_tracker.py)         │
│  - YOLOv8检测车辆                            │
│  - 追踪车辆ID                                │
└──────┬───────────────────────────────────────┘
       │ detections: [(track_id, bbox), ...]
       ↓
┌──────────────────────────────────────────────┐
│  ViolationDetector (violation_detector.py)   │
│  - 分析轨迹                                  │
│  - 检测违规                                  │
│  - 生成截图                                  │
└──────┬───────────────────────────────────────┘
       │ 发现违规
       ↓
┌──────────────────────────────────────────────┐
│  异步上报 (后台线程)                         │
│  1. BackendAPIClient.upload_image()          │
│  2. BackendAPIClient.report_violation()      │
└──────┬───────────────────────────────────────┘
       │ HTTP + JWT
       ↓
┌──────────────────────────────────────────────┐
│  Java后端 (Spring Boot)                      │
│  - FileController: 保存图片                  │
│  - ViolationController: 保存违规记录         │
└──────┬───────────────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────────────┐
│  数据库 (MySQL)                              │
│  - violations表                              │
│  - 文件系统: ./uploads/violation/...         │
└──────────────────────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────────────┐
│  前端查询违规记录                            │
│  GET /api/violations                         │
│  - 展示违规列表                              │
│  - 显示违规图片                              │
└──────────────────────────────────────────────┘
```

### 2. 单张图片检测流程

```
┌─────────────┐
│   前端UI    │
│  上传图片   │
└──────┬──────┘
       │ HTTP POST (multipart)
       ↓
┌──────────────────────────────────────────────┐
│  Java ImageDetectionController              │
│  - 接收图片                                  │
│  - 转发到Python服务                          │
└──────┬───────────────────────────────────────┘
       │ HTTP POST
       ↓
┌──────────────────────────────────────────────┐
│  Flask /detect-image                         │
│  - 接收multipart文件                         │
│  - 解析信号灯状态                            │
└──────┬───────────────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────────────┐
│  ImageViolationDetector                      │
│  - YOLOv8检测车辆                            │
│  - 检测闯红灯/压线                           │
│  - 生成截图                                  │
│  - 自动上报                                  │
└──────┬───────────────────────────────────────┘
       │ 返回检测结果
       ↓
┌──────────────────────────────────────────────┐
│  Java ImageDetectionController               │
│  - 接收AI检测结果                            │
│  - autoReportViolations() 自动上报           │
└──────┬───────────────────────────────────────┘
       │ 返回前端
       ↓
┌──────────────────────────────────────────────┐
│  前端展示                                    │
│  - 标注图片                                  │
│  - 违规列表                                  │
└──────────────────────────────────────────────┘
```

### 3. 信号灯同步流程

```
┌──────────────────────────────────────────────┐
│  前端: 切换信号源模式                        │
│  POST /api/traffic/signal-source-mode        │
│  {mode: "backend" | "simulation"}            │
└──────┬───────────────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────────────┐
│  Python Flask 接收模式切换请求               │
│  更新 current_signal_source_mode             │
└──────┬───────────────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────────────┐
│  后台定时任务 (每2秒执行)                    │
│                                              │
│  if mode == "backend":                       │
│    GET /api/traffic/signals/realtime         │
│    └→ 从Java后端获取信号                     │
│                                              │
│  elif mode == "simulation":                  │
│    use SimpleTrafficLightDetector            │
│    └→ 时间循环模拟（30秒周期）               │
└──────┬───────────────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────────────┐
│  WebSocket推送到前端                         │
│  socketio.emit('traffic', {                  │
│    signals: {...},                           │
│    leftTurnSignals: {...}                    │
│  })                                          │
└──────┬───────────────────────────────────────┘
       │
       ↓
┌──────────────────────────────────────────────┐
│  前端实时更新信号灯UI                        │
│  - 红灯/绿灯状态切换                         │
│  - 直行灯 + 左转灯分别显示                   │
└──────────────────────────────────────────────┘
```

---

## 调用关系

### Python模块调用关系

```
ai_realtime_service.py (Flask主服务)
  │
  ├─→ VehicleTracker (检测车辆)
  │     └─→ YOLOv8模型
  │
  ├─→ ViolationDetector (检测违规)
  │     ├─→ _update_trajectory()
  │     ├─→ detect_red_light_violation()
  │     ├─→ detect_wrong_way_violation()
  │     ├─→ detect_lane_change_violation()
  │     └─→ _report_to_backend()
  │           └─→ BackendAPIClient
  │                 ├─→ _login() → JWT
  │                 ├─→ upload_image() → FileController
  │                 └─→ report_violation() → ViolationController
  │
  └─→ SimpleTrafficLightDetector (信号模拟)
        └─→ get_signal_states()
```

### Java模块调用关系

```
Controller层
  │
  ├─→ AuthController
  │     └─→ AuthService
  │           ├─→ UserRepository
  │           └─→ JwtUtil
  │
  ├─→ ViolationController
  │     └─→ ViolationService
  │           ├─→ ViolationRepository (MySQL)
  │           ├─→ RedisService (计数)
  │           └─→ AlertWebSocketHandler (实时推送)
  │
  ├─→ FileController
  │     └─→ 文件系统 (./uploads/)
  │
  ├─→ ImageDetectionController
  │     ├─→ RestTemplate → Python Flask API
  │     └─→ ViolationService (自动上报)
  │
  └─→ SignalController
        └─→ SignalService
              ├─→ SignalRepository
              └─→ RedisService
```

### 跨服务调用关系

```
Frontend (React/Vue)
  │
  ├─→ WebSocket → Python Flask (实时检测)
  │     └─→ Java Backend (违规上报)
  │
  ├─→ HTTP → Java Backend
  │     ├─→ 认证登录
  │     ├─→ 查询违规
  │     ├─→ 上传图片
  │     └─→ 信号灯管理
  │
  └─→ HTTP → Java Backend → Python Flask (图片检测代理)

Python Flask
  │
  └─→ HTTP → Java Backend
        ├─→ JWT登录 (/api/auth/login)
        ├─→ 上传文件 (/api/files/upload)
        ├─→ 上报违规 (/api/violations/report)
        └─→ 获取信号 (/api/traffic/signals/realtime)
```

---

## 部署说明

### 开发环境

#### Python环境
```bash
# 安装依赖
cd ai_detection
pip install -r requirements.txt

# 下载YOLOv8模型
# yolov8s.pt会自动下载到 ~/.cache/ultralytics/

# 启动Flask服务
python api/ai_realtime_service.py
# 运行在 http://localhost:5000
```

#### Java环境
```bash
# 配置数据库
# 修改 src/main/resources/application.properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3307/traffic_mind
spring.datasource.username=root
spring.datasource.password=TrafficMind@2024

# 启动Redis
redis-server

# 启动Spring Boot
./mvnw spring-boot:run
# 运行在 http://localhost:8081
```

#### 前端环境
```bash
cd ai_detection/frontend-examples/vite-app
npm install
npm run dev
# 运行在 http://localhost:5173
```

### 生产环境

#### 文件路径配置
```properties
# application.properties
file.upload.base-path=./uploads  # 相对路径，跨平台兼容

# Linux部署
./uploads/ → /var/www/traffic-mind/uploads/

# Windows部署
./uploads/ → D:\traffic-mind\uploads\
```

#### 环境变量
```bash
# Python
export AI_SERVICE_PORT=5000
export BACKEND_API_URL=http://localhost:8081/api

# Java
export SPRING_DATASOURCE_URL=jdbc:mysql://mysql-server:3306/traffic_mind
export SPRING_DATASOURCE_PASSWORD=<production-password>
export JWT_SECRET=<production-secret>
```

#### Docker部署 (推荐)
```yaml
# docker-compose.yml
version: '3.8'
services:
  mysql:
    image: mysql:8
    environment:
      MYSQL_DATABASE: traffic_mind
      MYSQL_ROOT_PASSWORD: <password>
    volumes:
      - mysql-data:/var/lib/mysql

  redis:
    image: redis:7-alpine

  python-ai:
    build: ./ai_detection
    ports:
      - "5000:5000"
    depends_on:
      - java-backend
    volumes:
      - ./uploads:/app/uploads

  java-backend:
    build: .
    ports:
      - "8081:8081"
    depends_on:
      - mysql
      - redis
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/traffic_mind
    volumes:
      - ./uploads:/app/uploads

  frontend:
    build: ./ai_detection/frontend-examples/vite-app
    ports:
      - "80:80"
    depends_on:
      - java-backend
```

#### LLM 服务部署 (AutoDL 云端)

**LLM 服务独立部署在 AutoDL GPU 服务器**，不与主系统共用服务器。

**AutoDL 服务器配置**:
```
实例名称: u836978
GPU: RTX 3090 (24GB) × 1
CUDA: 12.4
Python: 3.10
框架: PyTorch + Transformers
```

**启动 LLM 服务**:
```bash
# 1. 连接 AutoDL 容器实例
# 使用 JupyterLab 或 SSH 连接

# 2. 进入项目目录
cd LLMlqy

# 3. 启动 LLM 服务
python run_open_LLM.py \
    --llm_model Qwen2.5-3B \
    --llm_path ../Qwen2.5-3B \
    --dataset hangzhou \
    --remote_url http://47.107.50.136:8081/api/traffic \
    --enable_remote
```

**LLM 服务地址**:
```
https://u836978-a67f-943bbb9f.westc.gpuhub.com:8443
```

**主服务器配置 LLM 地址** (`.env`):
```bash
# Java 后端配置
LLM_SERVICE_BASE_URL=https://u836978-a67f-943bbb9f.westc.gpuhub.com:8443
```

**通信流程**:
```
主服务器 (47.107.50.136)
    │
    │ HTTPS POST (交通数据)
    ▼
┌─────────────────────────────┐
│  AutoDL LLM 服务器          │
│  u836978-a67f-943bbb9f...  │
│  Qwen2.5-3B 推理服务        │
│  输出: "ETWT", "NSNL"...    │
└─────────────────────────────┘
    │
    │ HTTPS Response (相位决策)
    ▼
主服务器更新信号灯状态
```

**注意事项**:
1. LLM 服务需要 GPU 支持，推理速度约 100-200ms/次
2. 建议设置超时时间: 3秒
3. 主服务器应有降级方案（LLM 不可用时使用时间模拟）
4. AutoDL 按 GPU 时间计费，不用时请停止实例

---

## 安全配置

### JWT认证

**配置** (`application.properties`):
```properties
jwt.secret=<至少64字节的随机字符串>
jwt.expiration=604800000  # 7天
```

**认证流程**:
```
1. POST /api/auth/login → 获取accessToken
2. 所有后续请求携带: Authorization: Bearer <token>
3. Spring Security自动验证JWT
4. 失败返回401 Unauthorized
```

### 文件上传安全

**限制**:
```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

**验证**:
```java
// 路径遍历防护
String safeFilename = filename.replace("..", "").replace("/", "").replace("\\", "");

// 文件类型白名单
isAllowedExtension(extension) → jpg|jpeg|png|gif|bmp|webp
```

### 跨域配置

```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:5173"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

---

## 性能优化

### 异步处理

**Python异步上报**:
```python
# 违规检测后立即返回，不等待上报完成
threading.Thread(target=self._do_backend_report, daemon=True).start()
```

**Java异步配置**:
```properties
spring.task.execution.pool.core-size=8
spring.task.execution.pool.max-size=16
spring.task.execution.pool.queue-capacity=100
```

### 数据库优化

**批量插入**:
```properties
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
```

**索引**:
```sql
CREATE INDEX idx_violations_intersection ON violations(intersection_id);
CREATE INDEX idx_violations_occurred_at ON violations(occurred_at);
CREATE INDEX idx_violations_status ON violations(status);
```

### Redis缓存

**违规计数缓存**:
```java
// 避免每次查询数据库
redisService.incrementViolationCount();  // Redis INCR
redisService.getViolationCount();  // Redis GET
```

---

## 常见问题

### 1. JWT认证失败

**问题**: `[API] ❌ 登录响应中未找到 token`

**原因**: Java返回字段名是 `accessToken`，不是 `token`

**解决**:
```python
self.jwt_token = result.get('data', {}).get('accessToken')  # ✅
# NOT: result.get('data', {}).get('token')  # ❌
```

### 2. 文件上传失败 (HTTP 500)

**问题**: `Current request is not a multipart request`

**原因**: 手动设置了 `Content-Type: application/json`

**解决**:
```python
# 只设置Authorization，让requests自动处理multipart
headers = {'Authorization': f'Bearer {self.jwt_token}'}
response = requests.post(url, files=files, data=data, headers=headers)
```

### 3. 信号灯不更新

**问题**: 前端只显示直行灯，左转灯不变化

**原因**: WebSocket只发送了 `signals`，没有发送 `leftTurnSignals`

**解决**:
```python
socketio.emit('traffic', {
    'signals': current_signal_states,
    'leftTurnSignals': current_left_turn_signals  # ✅ 必须包含
})
```

### 4. 违规上报格式不匹配

**问题**: Python解析失败

**原因**: Java返回 `{id, message}`，不是 `{code: 200, data: {id}}`

**解决**:
```python
# 兼容两种格式
if 'id' in result:
    return result.get('id')  # ✅ 直接格式
elif result.get('code') == 200:
    return result.get('data', {}).get('id')  # 包装格式
```

---

## 版本信息

- **文档版本**: 1.0.0
- **最后更新**: 2025-12-27
- **系统版本**: TrafficMind 1.0
- **维护团队**: Coders (冯俊财、关镜文、路清怡、黄弋涵)

---

## 附录

### A. 违规类型枚举

| 枚举值 | 中文名称 | Python内部值 | 检测方法 |
|--------|---------|--------------|---------|
| RED_LIGHT | 闯红灯 | red_light_running | 轨迹穿越停止线 |
| WRONG_WAY | 逆行 | wrong_way_driving | 轨迹方向判断 |
| CROSS_SOLID_LINE | 跨实线 | lane_change_across_solid_line | 点到线距离 |
| ILLEGAL_TURN | 违法转弯 | illegal_turn | 待转区分析 |

### B. 方向枚举

| 枚举值 | 中文名称 | Python内部值 |
|--------|---------|--------------|
| NORTH | 北 | north_bound |
| SOUTH | 南 | south_bound |
| WEST | 西 | west_bound |
| EAST | 东 | east_bound |

### C. 转弯类型枚举

| 枚举值 | 中文名称 |
|--------|---------|
| STRAIGHT | 直行 |
| LEFT_TURN | 左转 |
| RIGHT_TURN | 右转 |
| U_TURN | 掉头 |

### D. ROI配置示例

```json
{
  "north_bound": {
    "stop_line": [[[x1,y1], [x2,y2], [x3,y3], [x4,y4]]],
    "direction_vector": [0, 1],
    "waiting_area": [[[x1,y1], [x2,y2], [x3,y3], [x4,y4]]]
  },
  "solid_lines": [
    {
      "name": "center_line",
      "direction": "north_south",
      "coordinates": [[x1, y1], [x2, y2]]
    }
  ]
}
```

---

**文档结束**
