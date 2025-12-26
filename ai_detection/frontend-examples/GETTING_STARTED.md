# 🚀 前端启动指南

这个文档将教你如何启动 AI 交通检测的前端演示界面。

---

## 📌 前提条件

### 1. 确保后端已启动

```bash
# 在项目根目录
cd d:\course_content\SE\seprojects\SE_project_backend

# 启动后端 API
python ai_detection/api/detection_api.py
```

看到这样的输出说明后端已成功启动：
```
 * Running on http://127.0.0.1:5000
 * Running on http://192.168.x.x:5000
```

---

## 🎯 三种启动方式

我为你准备了三种启动前端的方式，选择最适合你的：

---

## 方式 1️⃣: 纯 HTML（最简单，无需配置）

### ✅ 优点
- 零配置，直接浏览器打开
- 无需安装任何依赖
- 适合快速演示

### 📝 步骤

1. **找到文件**
   ```
   ai_detection/frontend-examples/demo.html
   ```

2. **用浏览器打开**
   - 双击 `demo.html` 文件
   - 或者右键 → 打开方式 → Chrome/Edge/Firefox

3. **开始使用**
   - 选择「图片检测」标签页，上传图片测试
   - 选择「实时监控」标签页，连接 WebSocket

### ⚠️ 注意
- 需要后端运行在 `http://localhost:5000`
- 如果遇到 CORS 错误，确保后端已启用 CORS

---

## 方式 2️⃣: Vite + React（推荐，现代化开发）

### ✅ 优点
- 热更新，改代码立即生效
- 完整的 TypeScript 支持
- 专业的开发体验
- 适合正式开发

### 📝 步骤

#### 1. 进入 Vite 项目目录
```bash
cd ai_detection/frontend-examples/vite-app
```

#### 2. 安装依赖
```bash
npm install
# 或
yarn install
# 或
pnpm install
```

#### 3. 启动开发服务器
```bash
npm run dev
```

#### 4. 打开浏览器
看到类似输出后：
```
  VITE v5.0.8  ready in 500 ms

  ➜  Local:   http://localhost:3000/
  ➜  Network: http://192.168.x.x:3000/
```

在浏览器访问 `http://localhost:3000`

### 📂 项目结构

```
vite-app/
├── src/
│   ├── App.tsx          # 主应用组件
│   ├── QuickStart.tsx   # 示例组件
│   ├── main.tsx         # 入口文件
│   └── index.css        # 样式
├── index.html           # HTML 模板
├── package.json         # 依赖配置
├── vite.config.ts       # Vite 配置
└── tsconfig.json        # TypeScript 配置
```

### 🔧 修改组件

编辑 `src/App.tsx` 来使用不同的组件：

```tsx
// 使用快速入门组件（默认）
import { QuickStartDemo } from './QuickStart'

function App() {
  return <QuickStartDemo />
}

// 或者使用完整功能组件
import { TrafficDetectionDemo } from './TrafficDetector'

function App() {
  return <TrafficDetectionDemo />
}
```

### 📦 构建生产版本

```bash
npm run build
```

构建产物在 `dist/` 目录。

---

## 方式 3️⃣: 集成到现有 React 项目

### 📝 步骤

#### 1. 安装依赖
```bash
npm install socket.io-client
```

#### 2. 复制组件文件
将以下文件复制到你的项目：
- `QuickStart.tsx` （简单版）
- 或 `TrafficDetector.tsx` （完整版）

#### 3. 导入使用
```tsx
import { QuickStartDemo } from './components/QuickStart'

function App() {
  return (
    <div className="App">
      <QuickStartDemo />
    </div>
  )
}
```

#### 4. 配置代理（可选）

如果你使用 Vite，编辑 `vite.config.ts`:

```typescript
export default defineConfig({
  server: {
    proxy: {
      '/detect-image': 'http://localhost:5000',
      '/socket.io': {
        target: 'http://localhost:5000',
        ws: true
      }
    }
  }
})
```

如果你使用 Create React App，编辑 `package.json`:

```json
{
  "proxy": "http://localhost:5000"
}
```

---

## 🧪 测试功能

### 测试图片检测

1. 准备一张交通图片（可以用项目中 `violations_images/` 里的图片）
2. 在前端界面点击「图片检测」
3. 上传图片
4. 点击「开始检测」
5. 查看检测结果

### 测试实时监控

1. 点击「实时监控」标签页
2. 输入任务ID（可选，默认为 `demo_task`）
3. 点击「连接」
4. 等待后端推送视频流和违规告警

---

## ❓ 常见问题

### 问题 1: 连接被拒绝 (Connection refused)

**原因:** 后端没有启动

**解决:**
```bash
python ai_detection/api/detection_api.py
```

---

### 问题 2: CORS 错误

**原因:** 跨域请求被阻止

**解决:** 检查后端 `detection_api.py` 是否启用了 CORS:

```python
from flask_cors import CORS
app = Flask(__name__)
CORS(app)  # ✅ 应该有这行
```

---

### 问题 3: WebSocket 连接失败

**检查清单:**
- [ ] 后端是否运行在 `localhost:5000`
- [ ] 防火墙是否阻止了连接
- [ ] 浏览器控制台是否有错误信息

---

### 问题 4: 图片上传失败

**检查:**
- 图片大小是否 < 10MB
- 图片格式是否为 JPG/PNG
- 后端日志是否有报错

---

### 问题 5: npm install 失败

**解决:**
```bash
# 清除缓存
npm cache clean --force

# 切换国内镜像
npm config set registry https://registry.npmmirror.com

# 重新安装
npm install
```

---

## 🎨 自定义界面

### HTML 版本

直接编辑 `demo.html` 的 `<style>` 部分:

```css
.header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  /* 改成你喜欢的颜色 */
}
```

### React 版本

编辑 `src/index.css` 或使用 CSS Modules/Styled Components。

---

## 📊 查看实时日志

### 浏览器控制台

按 `F12` 打开开发者工具，查看 Console 标签页：

```
✅ WebSocket 已连接
📸 收到帧: 125
🚨 检测到违规: red_light
```

### 后端日志

查看 Python 终端输出的日志信息。

---

## 🚀 性能优化建议

### 1. 图片压缩

上传前压缩大图片可以加快速度：

```javascript
// 在 demo.html 或组件中添加压缩逻辑
const compressImage = async (file) => {
  // 使用 canvas 压缩到 1920px 宽度
}
```

### 2. 限制违规记录数量

只保留最近的记录：

```javascript
setViolations(prev => [newViolation, ...prev].slice(0, 50))
```

### 3. WebSocket 重连

自动重连机制：

```javascript
const socket = io('http://localhost:5000', {
  reconnection: true,
  reconnectionDelay: 1000,
  reconnectionAttempts: 5
})
```

---

## 📖 扩展阅读

- [API 完整参考](api-reference.md)
- [组件使用文档](README.md)
- [Socket.IO 官方文档](https://socket.io/docs/v4/)

---

## 🆘 需要帮助？

- 📧 联系后端开发者
- 📁 查看示例代码
- 🐛 检查浏览器控制台错误

---

## ✅ 快速检查清单

启动前端前，确保：

- [ ] 后端已启动 (`python api/detection_api.py`)
- [ ] 后端运行在 `http://localhost:5000`
- [ ] 已安装 Node.js（Vite 方式需要）
- [ ] 已运行 `npm install`（Vite 方式需要）
- [ ] 浏览器支持 WebSocket（现代浏览器都支持）

---

**祝你使用愉快！🎉**

如果遇到问题，先检查：
1. 后端是否运行
2. 浏览器控制台报错
3. 网络连接是否正常
