# 📡 AI 检测模块 API 完整参考

> 后端服务地址: `http://localhost:5000`

---

## 📋 目录

1. [图片检测 API](#1-图片检测-api)
2. [实时监控 WebSocket](#2-实时监控-websocket)
3. [违规记录查询 API](#3-违规记录查询-api)
4. [信号灯控制 API](#4-信号灯控制-api)
5. [数据类型定义](#5-数据类型定义)

---

## 1. 图片检测 API

### `POST /detect-image`

上传单张图片进行违规检测。

#### 请求

**Content-Type:** `multipart/form-data`

**参数:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| image | File | ✅ | 图片文件 (JPG/PNG, < 10MB) |

**示例:**

```javascript
const formData = new FormData();
formData.append('image', imageFile);

const response = await fetch('http://localhost:5000/detect-image', {
  method: 'POST',
  body: formData
});

const result = await response.json();
```

#### 响应

**成功响应 (200):**

```json
{
  "success": true,
  "image_name": "traffic_001.jpg",
  "image_size": [1920, 1080],
  "total_violations": 2,
  "violations": [
    {
      "id": "v_1735123456_001",
      "type": "red_light",
      "track_id": 15,
      "direction": "SOUTH",
      "confidence": 0.95,
      "timestamp": "2024-12-25T10:30:45.123Z",
      "bbox": [450, 200, 550, 350]
    }
  ],
  "annotated_image": "base64_encoded_image_data...",
  "timestamp": "2024-12-25T10:30:45.123Z"
}
```

**失败响应 (400):**

```json
{
  "success": false,
  "error": "No image provided"
}
```

---

## 2. 实时监控 WebSocket

### 连接地址

```
ws://localhost:5000
```

### 客户端事件 (Emit)

#### `subscribe` - 订阅任务

订阅特定任务的实时数据推送。

**参数:**

```javascript
socket.emit('subscribe', {
  taskId: 'task_001'  // 必填: 任务ID
});
```

---

### 服务端事件 (On)

#### `connect` - 连接成功

```javascript
socket.on('connect', () => {
  console.log('WebSocket 已连接');
  console.log('Socket ID:', socket.id);
});
```

---

#### `frame` - 实时帧数据

每处理一帧视频都会推送。

**数据格式:**

```javascript
socket.on('frame', (data) => {
  console.log(data);
});
```

```json
{
  "taskId": "task_001",
  "frameNumber": 125,
  "progress": 45.5,
  "image": "base64_encoded_frame_data...",
  "violations": 3,
  "detections": [
    {
      "track_id": 5,
      "bbox": [100, 200, 300, 400],
      "class": "car",
      "confidence": 0.92,
      "direction": "SOUTH"
    }
  ]
}
```

**字段说明:**

- `frameNumber`: 当前帧编号
- `progress`: 处理进度 (0-100)
- `image`: 标注后的帧图片 (base64)
- `violations`: 当前帧违规数量
- `detections`: 检测到的车辆列表

---

#### `violation` - 违规告警

检测到违规行为时立即推送。

**数据格式:**

```javascript
socket.on('violation', (data) => {
  console.log('🚨 检测到违规!', data.violation);
});
```

```json
{
  "violation": {
    "id": "v_1735123456_015",
    "type": "red_light",
    "track_id": 15,
    "direction": "SOUTH",
    "confidence": 0.95,
    "timestamp": "2024-12-25T10:30:45.123Z",
    "bbox": [450, 200, 550, 350],
    "screenshot": "base64_encoded_violation_image..."
  }
}
```

---

#### `signal_update` - 信号灯状态更新

信号灯状态变化时推送。

**数据格式:**

```javascript
socket.on('signal_update', (data) => {
  console.log('信号灯更新:', data);
});
```

```json
{
  "north": "red",
  "south": "green",
  "east": "red",
  "west": "red"
}
```

**可能的值:** `"red"` | `"green"` | `"yellow"`

---

#### `complete` - 任务完成

视频处理完成时触发。

```javascript
socket.on('complete', () => {
  console.log('任务已完成');
});
```

---

#### `error` - 错误事件

```javascript
socket.on('error', (error) => {
  console.error('错误:', error);
});
```

---

#### `disconnect` - 连接断开

```javascript
socket.on('disconnect', () => {
  console.log('WebSocket 已断开');
});
```

---

### 完整示例

```javascript
import { io } from 'socket.io-client';

const socket = io('http://localhost:5000', {
  transports: ['websocket']
});

socket.on('connect', () => {
  console.log('✅ 连接成功');
  socket.emit('subscribe', { taskId: 'demo_task' });
});

socket.on('frame', (data) => {
  // 更新视频显示
  updateVideoFrame(data.image);
  updateProgress(data.progress);
});

socket.on('violation', (data) => {
  // 显示违规告警
  showAlert(data.violation);
});

socket.on('signal_update', (data) => {
  // 更新信号灯UI
  updateSignalLights(data);
});

socket.on('complete', () => {
  console.log('✅ 处理完成');
});

socket.on('disconnect', () => {
  console.log('❌ 连接断开');
});
```

---

## 3. 违规记录查询 API

### `GET /violations`

获取违规记录列表。

#### 请求

**查询参数:**

| 参数 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| type | string | ❌ | 违规类型过滤 | `red_light` |
| direction | string | ❌ | 方向过滤 | `SOUTH` |
| limit | number | ❌ | 返回数量限制 | `50` |
| offset | number | ❌ | 跳过数量 | `0` |

**示例:**

```javascript
// 获取所有违规
const response = await fetch('http://localhost:5000/violations');

// 获取最近 50 条闯红灯记录
const response = await fetch(
  'http://localhost:5000/violations?type=red_light&limit=50'
);

// 分页查询
const response = await fetch(
  'http://localhost:5000/violations?limit=20&offset=40'
);
```

#### 响应

```json
{
  "success": true,
  "total": 156,
  "violations": [
    {
      "id": "v_1735123456_001",
      "type": "red_light",
      "track_id": 15,
      "direction": "SOUTH",
      "confidence": 0.95,
      "timestamp": "2024-12-25T10:30:45.123Z",
      "bbox": [450, 200, 550, 350],
      "screenshot": "path/to/screenshot.jpg"
    }
  ]
}
```

---

### `GET /violations/:violation_id`

获取单个违规记录的详细信息。

#### 请求

```javascript
const response = await fetch(
  'http://localhost:5000/violations/v_1735123456_001'
);
```

#### 响应

```json
{
  "success": true,
  "violation": {
    "id": "v_1735123456_001",
    "type": "red_light",
    "track_id": 15,
    "direction": "SOUTH",
    "confidence": 0.95,
    "timestamp": "2024-12-25T10:30:45.123Z",
    "bbox": [450, 200, 550, 350],
    "screenshot": "base64_encoded_image...",
    "metadata": {
      "vehicle_class": "car",
      "signal_status": "red",
      "intersection_id": 1
    }
  }
}
```

---

## 4. 信号灯控制 API

### `GET /signal-status/:intersection_id`

获取指定路口的信号灯状态。

#### 请求

```javascript
const response = await fetch('http://localhost:5000/signal-status/1');
```

#### 响应

```json
{
  "success": true,
  "intersection_id": 1,
  "status": {
    "north": "green",
    "south": "green",
    "east": "red",
    "west": "red"
  },
  "timestamp": "2024-12-25T10:30:45.123Z"
}
```

---

### `POST /start-realtime`

启动实时检测任务。

#### 请求

**Content-Type:** `application/json`

**参数:**

```json
{
  "taskId": "task_001",
  "videoUrl": "/path/to/video.mp4",
  "intersectionId": 1,
  "direction": "SOUTH"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| taskId | string | ✅ | 任务唯一标识 |
| videoUrl | string | ✅ | 视频文件路径或URL |
| intersectionId | number | ✅ | 路口ID |
| direction | string | ✅ | 检测方向 (NORTH/SOUTH/EAST/WEST) |

**示例:**

```javascript
const response = await fetch('http://localhost:5000/start-realtime', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    taskId: `task_${Date.now()}`,
    videoUrl: './videos/traffic.mp4',
    intersectionId: 1,
    direction: 'SOUTH'
  })
});

const result = await response.json();
console.log('任务ID:', result.taskId);
```

#### 响应

```json
{
  "success": true,
  "taskId": "task_1735123456",
  "message": "Realtime task started successfully"
}
```

---

## 5. 数据类型定义

### TypeScript 类型定义

```typescript
// ==================== 违规相关 ====================

type ViolationType = 'red_light' | 'wrong_way' | 'lane_change';

type Direction = 'NORTH' | 'SOUTH' | 'EAST' | 'WEST';

interface Violation {
  id: string;
  type: ViolationType;
  track_id: number;
  direction: Direction;
  confidence: number;
  timestamp: string;
  bbox: [number, number, number, number];
  screenshot?: string;
}

// ==================== 车辆检测 ====================

type VehicleClass = 'car' | 'truck' | 'bus' | 'motorcycle';

interface VehicleDetection {
  track_id: number;
  bbox: [number, number, number, number];
  class: VehicleClass;
  confidence: number;
  direction: string;
}

// ==================== 帧数据 ====================

interface FrameData {
  taskId: string;
  frameNumber: number;
  progress: number;
  image: string;
  violations: number;
  detections: VehicleDetection[];
}

// ==================== 信号灯 ====================

type SignalLight = 'red' | 'green' | 'yellow';

interface SignalStatus {
  north: SignalLight;
  south: SignalLight;
  east: SignalLight;
  west: SignalLight;
}

// ==================== API 响应 ====================

interface DetectImageResponse {
  success: boolean;
  image_name: string;
  image_size: [number, number];
  total_violations: number;
  violations: Violation[];
  annotated_image: string;
  timestamp: string;
}

interface ViolationsResponse {
  success: boolean;
  total: number;
  violations: Violation[];
}

interface SignalStatusResponse {
  success: boolean;
  intersection_id: number;
  status: SignalStatus;
  timestamp: string;
}
```

---

## 🔧 错误处理

### 常见错误码

| HTTP 状态码 | 说明 | 解决方法 |
|------------|------|----------|
| 400 | 请求参数错误 | 检查请求参数格式 |
| 404 | 资源不存在 | 检查 URL 是否正确 |
| 500 | 服务器内部错误 | 查看后端日志 |

### 错误响应格式

```json
{
  "success": false,
  "error": "错误描述信息"
}
```

---

## 📝 注意事项

1. **图片大小限制:** 上传图片建议 < 10MB
2. **WebSocket 重连:** 建议实现自动重连机制
3. **Base64 图片:** 显示时需要添加 `data:image/jpeg;base64,` 前缀
4. **坐标系统:** bbox 格式为 `[x1, y1, x2, y2]`，左上角为原点
5. **时间格式:** 所有时间戳使用 ISO 8601 格式

---

##  性能建议

- 使用 WebSocket 而不是轮询以获得实时数据
- 限制违规记录列表长度 (如只保留最近 50 条)
- 对上传的图片进行压缩
- 实现分页加载违规记录

---

**文档版本:** v1.0
**最后更新:** 2024-12-25
**维护者:** TrafficMind 团队
