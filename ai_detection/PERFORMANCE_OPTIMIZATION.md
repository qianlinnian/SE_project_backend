#  性能优化指南

当前视频处理速度: **~5 FPS**
目标: 提升到 **15-30 FPS**

---

## 📊 当前性能瓶颈分析

从日志看到：
```
处理时间: 27.73秒
总帧数: 150
实际FPS: 5.41
```

主要瓶颈：
1. **YOLOv8 模型推理慢** - 每帧 ~180ms
2. **CPU 处理** - 没有使用 GPU 加速
3. **帧率过高** - 原视频 30FPS，全部处理

---

## 🎯 优化方案

### 方案 1: 启用 GPU 加速 ⭐⭐⭐⭐⭐

**效果:** 速度提升 **5-10倍**

#### Windows + NVIDIA GPU

```bash
# 1. 检查CUDA是否可用
python -c "import torch; print(torch.cuda.is_available())"

# 2. 如果显示 False，安装 CUDA 版本的 PyTorch
pip uninstall torch torchvision
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu118

# 3. 验证
python -c "import torch; print(f'CUDA: {torch.cuda.is_available()}, Device: {torch.cuda.get_device_name(0) if torch.cuda.is_available() else None}')"
```

YOLOv8 会**自动使用 GPU**，无需修改代码！

---

### 方案 2: 降低处理帧率 ⭐⭐⭐⭐

**效果:** 速度提升 **2-3倍**，几乎不影响检测准确性

编辑 `ai_detection/api/ai_realtime_service.py`:

```python
# 第 76 行
TARGET_FPS = 12  # 降低到 10 或更低
```

改为：

```python
TARGET_FPS = 10  # 30FPS -> 10FPS，处理速度提升 3倍
# 或
TARGET_FPS = 6   # 30FPS -> 6FPS，处理速度提升 5倍
```

**说明:**
- 原视频 30FPS，但检测违规不需要那么高帧率
- 降到 10FPS 仍然足够捕捉违规行为
- 6FPS 对于闯红灯检测也完全够用

---

### 方案 3: 使用更小的模型 ⭐⭐⭐

**效果:** 速度提升 **2-3倍**，准确率略降

编辑 `ai_detection/api/ai_realtime_service.py`:

```python
# 第 70 行
MODEL_PATH = "./yolov8s.pt"  # Small 模型
```

改为：

```python
MODEL_PATH = "./yolov8n.pt"  # Nano 模型 - 最快但准确率稍低
# 或
MODEL_PATH = "./yolov8m.pt"  # Medium 模型 - 平衡点（如果GPU够好）
```

**模型对比:**

| 模型 | 速度 | 准确率 | 推荐场景 |
|------|------|--------|----------|
| yolov8n | ⚡⚡⚡⚡⚡ | ⭐⭐⭐ | CPU处理、实时性要求高 |
| yolov8s | ⚡⚡⚡⚡ | ⭐⭐⭐⭐ | **当前使用，平衡** |
| yolov8m | ⚡⚡⚡ | ⭐⭐⭐⭐⭐ | GPU处理、准确率优先 |

---

### 方案 4: 降低图片质量 ⭐⭐

**效果:** 减少带宽和传输时间

编辑 `ai_detection/api/ai_realtime_service.py`:

```python
# 第 77 行
JPEG_QUALITY = 70  # JPEG 压缩质量
```

改为：

```python
JPEG_QUALITY = 50  # 降低画质，加快传输
```

---

### 方案 5: 跳帧处理 ⭐⭐⭐⭐

**效果:** 速度提升 **2-5倍**

修改处理逻辑，每 N 帧处理一次：

```python
# 在 process_video_realtime 函数中
frame_skip = 2  # 每2帧处理1帧
frame_count = 0

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break

    frame_count += 1
    if frame_count % frame_skip != 0:
        continue  # 跳过这一帧

    # 处理帧...
```

---

## 🖥️ 服务器部署优化

### Linux 服务器配置

```bash
# 1. 使用 Gunicorn + Gevent (生产级WSGI服务器)
pip install gunicorn gevent

# 2. 启动服务
gunicorn -k gevent -w 4 -b 0.0.0.0:5000 --timeout 300 \
    'ai_detection.api.ai_realtime_service:app'
```

### Docker 部署（推荐）

```dockerfile
FROM nvidia/cuda:11.8.0-cudnn8-runtime-ubuntu22.04

# 安装 Python 和依赖
RUN apt-get update && apt-get install -y python3-pip
COPY requirements.txt .
RUN pip install -r requirements.txt

# 复制代码
COPY ai_detection /app/ai_detection
WORKDIR /app/ai_detection

# 启动服务
CMD ["gunicorn", "-k", "gevent", "-w", "4", "-b", "0.0.0.0:5000", \
     "api.ai_realtime_service:app"]
```

---

## 📈 综合优化建议

### 开发环境（本地测试）

```python
# ai_detection/api/ai_realtime_service.py

# 配置
MODEL_PATH = "./yolov8n.pt"     # 使用 Nano 模型
TARGET_FPS = 6                   # 降低帧率
JPEG_QUALITY = 50                # 降低画质
```

**预期速度:** 15-20 FPS

---

### 生产环境（服务器部署）

#### GPU 服务器配置

```python
# ai_detection/api/ai_realtime_service.py

MODEL_PATH = "./yolov8s.pt"     # Small 模型 + GPU
TARGET_FPS = 12                  # 适中帧率
JPEG_QUALITY = 70                # 保持画质
```

**预期速度:** 25-30 FPS（GPU加速）

#### CPU 服务器配置

```python
MODEL_PATH = "./yolov8n.pt"     # Nano 模型
TARGET_FPS = 8                   # 较低帧率
JPEG_QUALITY = 50                # 降低画质
frame_skip = 2                   # 跳帧处理
```

**预期速度:** 12-18 FPS

---

## 🔍 性能监控

### 添加性能日志

```python
import time

# 在处理循环中
start = time.time()
# ... 处理代码 ...
process_time = time.time() - start
print(f"帧处理时间: {process_time*1000:.1f}ms, FPS: {1/process_time:.1f}")
```

### 分析瓶颈

```python
# YOLO 推理时间
yolo_start = time.time()
results = model(frame)
print(f"YOLO 推理: {(time.time()-yolo_start)*1000:.1f}ms")

# 追踪时间
track_start = time.time()
tracks = tracker.update(detections)
print(f"追踪: {(time.time()-track_start)*1000:.1f}ms")
```

---

## ✅ 快速优化清单

**5分钟优化（建议先做）:**

- [ ] 修改 `TARGET_FPS = 8`
- [ ] 修改 `JPEG_QUALITY = 50`
- [ ] 重启服务测试

**15分钟优化（中级）:**

- [ ] 下载 `yolov8n.pt` 模型
- [ ] 修改 `MODEL_PATH = "./yolov8n.pt"`
- [ ] 重启服务测试

**1小时优化（高级）:**

- [ ] 安装 CUDA + cuDNN
- [ ] 安装 GPU 版本 PyTorch
- [ ] 验证 GPU 可用
- [ ] 重启服务（自动使用GPU）

---

## 🎯 预期效果对比

| 配置 | FPS | 处理时间(150帧) | 说明 |
|------|-----|----------------|------|
| 当前 | 5.4 | 27.7s | CPU + yolov8s + 12FPS |
| 优化1 | 12+ | 12.5s | TARGET_FPS=8, QUALITY=50 |
| 优化2 | 18+ | 8.3s | + yolov8n 模型 |
| 优化3 | 30+ | 5.0s | + GPU 加速 ⭐ |

---

## 💡 总结

**最优方案组合:**

1. **立即可做:** 降低 TARGET_FPS 到 8，QUALITY 到 50
2. **中期优化:** 换用 yolov8n 模型
3. **长期方案:** 服务器部署 + GPU 加速

**部署到服务器的优势:**
✅ 更强的CPU/GPU性能
✅ 不占用本地资源
✅ 稳定的网络环境
✅ 可以同时处理多个任务

建议**优先尝试方案1+2**，可以在不增加成本的情况下提升2-3倍速度！
