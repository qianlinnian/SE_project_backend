import { useState } from 'react'
import { QuickStartDemo } from './QuickStart'
import { DualVideoMonitor } from './DualVideoMonitor'

function App() {
  const [mode, setMode] = useState<'quick' | 'dual'>('quick')

  return (
    <div>
      {/* 模式切换按钮 */}
      <div style={{
        position: 'fixed',
        top: '10px',
        right: '10px',
        zIndex: 1000,
        display: 'flex',
        gap: '10px'
      }}>
        <button
          onClick={() => setMode('quick')}
          style={{
            padding: '8px 16px',
            background: mode === 'quick' ? '#2196f3' : '#666',
            color: '#fff',
            border: 'none',
            borderRadius: '5px',
            cursor: 'pointer',
            fontSize: '12px'
          }}
        >
          📸 快速入门
        </button>
        <button
          onClick={() => setMode('dual')}
          style={{
            padding: '8px 16px',
            background: mode === 'dual' ? '#4caf50' : '#666',
            color: '#fff',
            border: 'none',
            borderRadius: '5px',
            cursor: 'pointer',
            fontSize: '12px'
          }}
        >
          📺 双窗口监控
        </button>
      </div>

      {/* 内容区域 */}
      {mode === 'quick' ? <QuickStartDemo /> : <DualVideoMonitor />}
    </div>
  )
}

export default App
