# 信号灯模式控制 API 使用说明

## 功能概述

AI检测服务支持4种信号灯数据源模式：

| 模式 | 说明 | 用途 |
|------|------|------|
| `llm` | 从LLM获取信号相位数据 | 使用智能信号控制系统（默认） |
| `backend` | 从Java后端REST API获取 | 使用传统固定时长信号灯 |
| `simulation` | 基于系统时间的60秒周期模拟 | 测试和演示 |
| `stop` | **停止信号灯同步** | 手动控制或暂停检测 |

---

## API 接口

### 1. 获取当前模式

**请求**:
```bash
GET /signal-mode
```

**响应**:
```json
{
  "success": true,
  "mode": "llm",
  "active_source": "llm",
  "last_update": "2025-01-03T12:00:00"
}
```

---

### 2. 切换信号灯模式

**请求**:
```bash
POST /signal-mode
Content-Type: application/json

{
  "mode": "stop"
}
```

**响应**:
```json
{
  "success": true,
  "message": "信号灯模式已切换为: stop",
  "old_mode": "llm",
  "new_mode": "stop"
}
```

---

### 3. 获取模式详情

**请求**:
```bash
GET /api/traffic/signal-source-mode
```

**响应**:
```json
{
  "success": true,
  "mode": "stop",
  "description": "停止同步",
  "activeSource": "stop",
  "activeSourceDescription": "停止同步",
  "lastCheckTime": "2025-01-03T12:00:00",
  "availableMode": {
    "llm": "LLM 数据",
    "backend": "Java 后端",
    "simulation": "时间模拟",
    "stop": "停止同步"
  }
}
```

---

## 使用示例

### 示例1: 停止信号灯自动更新

**场景**: 需要手动控制信号灯状态，不希望自动同步覆盖

```bash
# 1. 切换到 stop 模式
curl -X POST http://47.107.50.136:5000/signal-mode \
  -H "Content-Type: application/json" \
  -d '{"mode": "stop"}'

# 2. 手动设置信号灯状态
curl -X POST http://47.107.50.136:5000/api/traffic \
  -H "Content-Type: application/json" \
  -d '{
    "north_bound": "red",
    "south_bound": "red",
    "east_bound": "green",
    "west_bound": "green"
  }'

# 3. 验证状态
curl http://47.107.50.136:5000/api/traffic/status
```

**结果**:
- 信号灯状态保持为手动设置的值，不会被自动同步覆盖
- 违规检测仍然正常工作，使用当前固定的信号灯状态

---

### 示例2: 恢复自动同步

**场景**: 完成手动控制后，恢复从LLM获取信号灯数据

```bash
# 切换回 llm 模式
curl -X POST http://47.107.50.136:5000/signal-mode \
  -H "Content-Type: application/json" \
  -d '{"mode": "llm"}'
```

**结果**:
- 后台线程继续每2秒从LLM获取最新信号相位
- 信号灯状态自动更新

---

### 示例3: 切换到时间模拟

**场景**: 在没有后端数据时进行测试

```bash
# 切换到 simulation 模式
curl -X POST http://47.107.50.136:5000/api/traffic/signal-source-mode \
  -H "Content-Type: application/json" \
  -d '{"mode": "simulation"}'
```

**时间模拟周期** (60秒循环):
- 0-20秒: 南北直行绿灯
- 20-23秒: 南北黄灯
- 23-43秒: 东西直行+左转绿灯
- 43-46秒: 东西黄灯
- 46-50秒: 南北左转绿灯
- 50-53秒: 南北左转黄灯
- 53-60秒: 全红等待

---

## 技术实现

### 核心逻辑

在 `fetch_signal_states_from_backend()` 函数中：

```python
# 停止模式：不更新信号灯状态
if mode == 'stop':
    current_active_source = 'stop'
    # 不更新 last_source_check_time，保持上次同步时间
    return True
```

- 后台同步线程仍在运行（每2秒检查一次）
- 当模式为 `stop` 时，直接返回不执行任何更新
- 信号灯状态保持在上一次设置的值

---

## 前端集成建议

### Vue 3 示例

```vue
<template>
  <div>
    <select v-model="signalMode" @change="changeMode">
      <option value="llm">LLM智能控制</option>
      <option value="backend">Java后端</option>
      <option value="simulation">时间模拟</option>
      <option value="stop">⏸️ 停止同步</option>
    </select>

    <div v-if="signalMode === 'stop'" class="manual-control">
      <h3>手动控制信号灯</h3>
      <!-- 信号灯手动控制界面 -->
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const signalMode = ref('llm')

onMounted(async () => {
  // 获取当前模式
  const { data } = await axios.get('http://47.107.50.136:5000/signal-mode')
  signalMode.value = data.mode
})

async function changeMode() {
  await axios.post('http://47.107.50.136:5000/signal-mode', {
    mode: signalMode.value
  })
}
</script>
```

---

## 注意事项

1. **stop 模式不会停止后台线程**
   - 同步任务仍在运行，只是跳过更新
   - 随时可以切换回其他模式恢复同步

2. **信号灯状态的优先级**
   - `stop` 模式下，手动设置的状态优先级最高
   - 切换回其他模式后，会立即被自动同步覆盖

3. **违规检测不受影响**
   - 无论哪种模式，违规检测器都使用当前全局信号灯状态
   - `stop` 模式只是停止更新，不影响检测逻辑

4. **线程安全**
   - 所有信号灯状态读写都通过 `signal_lock` 保护
   - 模式切换通过 `signal_mode_lock` 保护

---

## 常见问题

**Q: stop 模式下可以手动更新信号灯状态吗？**

A: 可以！使用 `POST /api/traffic` 接口手动设置，状态会一直保持到下次切换模式。

**Q: 如何知道当前是否在使用 stop 模式？**

A: 调用 `GET /signal-mode` 或 `GET /api/traffic/signal-source-mode` 查看 `mode` 字段。

**Q: stop 模式会影响视频检测性能吗？**

A: 不会！`stop` 只是跳过信号灯更新，不影响 YOLOv8 检测和违规判断。

**Q: 能否在视频处理中途切换模式？**

A: 可以！模式切换是实时生效的，不需要重启服务或中断视频处理。

---

## 完整工作流示例

### 场景：手动测试闯红灯检测

```bash
# 1. 停止自动同步
curl -X POST http://47.107.50.136:5000/signal-mode \
  -H "Content-Type: application/json" \
  -d '{"mode": "stop"}'

# 2. 设置南北方向为红灯
curl -X POST http://47.107.50.136:5000/api/traffic \
  -H "Content-Type: application/json" \
  -d '{
    "north_bound": "red",
    "south_bound": "red",
    "east_bound": "green",
    "west_bound": "green"
  }'

# 3. 启动视频检测（此时南北方向车辆闯红灯会被检测到）
curl -X POST http://47.107.50.136:5000/test-local \
  -H "Content-Type: application/json" \
  -d '{"videoName": "car_1_cross.mp4"}'

# 4. 测试完成后，切换回LLM模式
curl -X POST http://47.107.50.136:5000/signal-mode \
  -H "Content-Type: application/json" \
  -d '{"mode": "llm"}'
```

---

## 总结

通过 `stop` 模式，你可以：
✅ 完全停止信号灯自动同步
✅ 手动控制信号灯状态用于测试
✅ 随时恢复自动同步
✅ 不影响违规检测功能

这为系统提供了更大的灵活性，特别适用于演示、测试和特殊场景下的手动控制。
