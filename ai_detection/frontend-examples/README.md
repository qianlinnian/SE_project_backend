# 🚗 AI 交通检测 - 前端集成指南

这个文件夹包含了 **前端开发者** 需要的所有示例代码和文档，帮助你快速集成 AI 交通检测功能。

---

##  快速开始

**👉 想要立即看到效果？查看 → [GETTING_STARTED.md](GETTING_STARTED.md)**

---

## 📁 文件说明

| 文件/文件夹 | 说明 | 适合人群 |
|------|------|----------|
| `GETTING_STARTED.md` |  **启动指南** - 如何运行前端 | **所有人（先看这个！）** |
| `demo.html` | 🌐 **纯 HTML 演示** - 双击即可打开 | 快速演示、无需配置 |
| `vite-app/` | ⚡ **Vite + React 项目** - 完整开发环境 | 正式开发 |
| `QuickStart.tsx` | 📝 **简单示例组件** - 核心功能演示 | 初学者、理解 API |
| `TrafficDetector.tsx` | 📦 **完整功能组件** - 生产级代码 | 正式项目集成 |
| `api-reference.md` | 📡 **API 完整参考** - 详细接口文档 | API 集成开发 |
| `README.md` | 📖 本文档 - 组件和 API 速查 | 所有人 |

---

### 方式 1: HTML（最简单）
双击打开 `demo.html`，无需任何配置！

### 方式 2: Vite + React（推荐）
```bash
cd vite-app
npm install
npm run dev
# 访问 http://localhost:3000
```

### 方式 3: 集成到现有项目
复制 `QuickStart.tsx` 或 `TrafficDetector.tsx` 到你的项目。

**详细步骤请查看 → [GETTING_STARTED.md](GETTING_STARTED.md)**

---

## 🔌 API 接口速查

### 图片检测 API

**端点:** `POST /detect-image`

**请求:**
```javascript
const formData = new FormData();
formData.append('image', imageFile);

fetch('http://localhost:5000/detect-image', {
  method: 'POST',
  body: formData
});
```

**响应:**
```json
{
  "success": true,
  "total_violations": 2,
  "violations": [
    {
      "id": "v_001",
      "type": "red_light",
      "direction": "SOUTH",
      "confidence": 0.95,
      "timestamp": "2024-01-01T12:00:00",
      "track_id": 123
    }
  ],
  "annotated_image": "base64_encoded_image_string",
  "image_size": [1920, 1080]
}
```

---

### 实时监控 API

**端点:** WebSocket `ws://localhost:5000`

**1. 连接 WebSocket**
```javascript
import { io } from 'socket.io-client';

const socket = io('http://localhost:5000', {
  transports: ['websocket']
});
```

**2. 订阅任务**
```javascript
socket.on('connect', () => {
  socket.emit('subscribe', { taskId: 'your_task_id' });
});
```

**3. 接收事件**
```javascript
// 接收实时视频帧
socket.on('frame', (data) => {
  console.log('帧号:', data.frameNumber);
  console.log('图片:', data.image); // base64
  console.log('检测数:', data.detections.length);
});

// 接收违规告警
socket.on('violation', (data) => {
  console.log('违规类型:', data.violation.type);
  console.log('置信度:', data.violation.confidence);
});

// 接收信号灯更新
socket.on('signal_update', (data) => {
  console.log('南向:', data.south); // 'red' | 'green' | 'yellow'
  console.log('北向:', data.north);
});
```

---

### 其他 API

```javascript
// 获取违规记录列表
GET /violations
GET /violations?type=red_light&limit=50

// 获取信号灯状态
GET /signal-status/{intersection_id}

// 启动实时检测任务
POST /start-realtime
{
  "taskId": "task_001",
  "videoUrl": "path/to/video.mp4",
  "intersectionId": 1,
  "direction": "SOUTH"
}
```

---

## 📦 组件使用示例

### 示例 1: 图片检测

```tsx
import { SimpleImageDetector } from './QuickStart';

function App() {
  return <SimpleImageDetector />;
}
```

**功能:**
- ✅ 上传图片
- ✅ 调用 API 检测违规
- ✅ 显示标注后的图片
- ✅ 显示违规列表

---

### 示例 2: 实时监控

```tsx
import { SimpleRealtimeMonitor } from './QuickStart';

function App() {
  return <SimpleRealtimeMonitor />;
}
```

**功能:**
- ✅ WebSocket 连接
- ✅ 实时视频流显示
- ✅ 违规告警列表
- ✅ 连接状态指示

---

### 示例 3: 完整功能

```tsx
import { TrafficDetectionDemo } from './TrafficDetector';

function App() {
  return <TrafficDetectionDemo />;
}
```

**功能:**
- ✅ 图片检测 + 实时监控
- ✅ 任务管理
- ✅ 违规列表
- ✅ 信号灯状态
- ✅ 进度显示

---

## 🎨 自定义样式

示例代码使用了内联样式，你可以轻松替换为你自己的 CSS:

```tsx
// 将内联样式替换为 className
<div style={{ padding: '20px' }}>  // ❌
<div className="container">        // ✅
```

---

## 🔧 常见问题

### ❓ CORS 错误

如果遇到跨域问题，确保后端已启用 CORS:

```python
# backend/api/detection_api.py
from flask_cors import CORS

app = Flask(__name__)
CORS(app)  # ✅ 已配置
```

### ❓ WebSocket 连接失败

检查：
1. 后端是否运行: `http://localhost:5000`
2. 防火墙是否阻止连接
3. 浏览器控制台是否有错误

### ❓ 图片上传失败

确保：
1. 文件大小 < 10MB
2. 文件格式为 JPG/PNG
3. FormData 的 key 是 `'image'`

### ❓ base64 图片不显示

正确格式:
```tsx
<img src={`data:image/jpeg;base64,${base64String}`} />
```

---

## 📊 数据类型定义

```typescript
// 违规类型
type ViolationType = 'red_light' | 'wrong_way' | 'lane_change';

// 违规记录
interface Violation {
  id: string;
  type: ViolationType;
  track_id: number;
  direction: 'NORTH' | 'SOUTH' | 'EAST' | 'WEST';
  confidence: number;  // 0-1
  timestamp: string;   // ISO 8601
  bbox: [number, number, number, number]; // [x1, y1, x2, y2]
  screenshot?: string; // base64
}

// 车辆检测
interface VehicleDetection {
  track_id: number;
  bbox: [number, number, number, number];
  class: 'car' | 'truck' | 'bus' | 'motorcycle';
  confidence: number;
  direction: string;
}

// 信号灯状态
interface SignalStatus {
  north: 'red' | 'green' | 'yellow';
  south: 'red' | 'green' | 'yellow';
  east: 'red' | 'green' | 'yellow';
  west: 'red' | 'green' | 'yellow';
}
```

---

##  性能优化建议

### 1. 图片压缩

上传前压缩图片可以提高速度:

```javascript
// 使用 canvas 压缩
const compressImage = (file, maxWidth = 1920) => {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement('canvas');
        const ratio = Math.min(maxWidth / img.width, 1);
        canvas.width = img.width * ratio;
        canvas.height = img.height * ratio;

        const ctx = canvas.getContext('2d');
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);

        canvas.toBlob(resolve, 'image/jpeg', 0.8);
      };
      img.src = e.target.result;
    };
    reader.readAsDataURL(file);
  });
};
```

### 2. WebSocket 重连

自动重连机制:

```javascript
const socket = io('http://localhost:5000', {
  transports: ['websocket'],
  reconnection: true,
  reconnectionDelay: 1000,
  reconnectionAttempts: 5
});
```

### 3. 限制违规列表长度

只保留最近的记录:

```javascript
setViolations(prev => [newViolation, ...prev].slice(0, 50));
```

---

## 📞 需要帮助?

- 📧 联系后端开发者: 冯俊财、关镜文
- 📁 查看完整代码: `TrafficDetector.tsx`
- 🐛 报告问题: 在项目 GitHub 提 Issue

---

## 📝 更新日志

- **2024-12-25**: 创建快速入门示例
- **2024-12-25**: 添加完整功能组件
- **2024-12-25**: 完善文档说明

---

**祝开发顺利! 🎉**
