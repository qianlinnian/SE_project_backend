/**
 * 🚀 AI 交通检测 - 快速入门示例
 *
 * 这是一个最简化的示例，适合前端开发者快速理解如何使用 AI 检测 API
 *
 * 📚 使用方法:
 * 1. 确保后端 API 运行在 http://localhost:5000
 * 2. 复制需要的组件到你的项目中
 * 3. 根据需要修改样式和功能
 */

import React, { useState } from 'react';

// ============================================
// 📸 示例 1: 最简单的图片检测
// ============================================

/**
 * 功能: 上传图片 → 检测违规 → 显示结果
 *
 * API 接口: POST /detect-image
 * 请求: FormData with 'image' field
 * 响应: { success, total_violations, violations, annotated_image }
 */
export const SimpleImageDetector = () => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);

  const handleDetect = async () => {
    if (!selectedFile) return;

    setLoading(true);

    // 创建 FormData
    const formData = new FormData();
    formData.append('image', selectedFile);

    try {
      // 调用 API
      const response = await fetch('http://localhost:5000/detect-image', {
        method: 'POST',
        body: formData,
      });

      const data = await response.json();
      setResult(data);
    } catch (error) {
      console.error('检测失败:', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: '20px', maxWidth: '800px' }}>
      <h2>图片违规检测</h2>

      {/* 文件选择 */}
      <input
        type="file"
        accept="image/*"
        onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
      />

      {/* 检测按钮 */}
      <button
        onClick={handleDetect}
        disabled={!selectedFile || loading}
        style={{ marginLeft: '10px' }}
      >
        {loading ? '检测中...' : '开始检测'}
      </button>

      {/* 显示结果 */}
      {result && (
        <div style={{ marginTop: '20px' }}>
          <h3>检测到 {result.total_violations} 个违规</h3>

          {/* 标注后的图片 */}
          {result.annotated_image && (
            <img
              src={`data:image/jpeg;base64,${result.annotated_image}`}
              alt="检测结果"
              style={{ maxWidth: '100%', border: '1px solid #ccc' }}
            />
          )}

          {/* 违规详情 */}
          {result.violations?.map((v: any, index: number) => (
            <div key={index} style={{
              padding: '10px',
              margin: '10px 0',
              background: '#f0f0f0'
            }}>
              <strong>违规类型:</strong> {v.type} <br />
              <strong>方向:</strong> {v.direction} <br />
              <strong>置信度:</strong> {(v.confidence * 100).toFixed(1)}%
            </div>
          ))}
        </div>
      )}
    </div>
  );
};


// ============================================
// 🎥 示例 2: 实时视频监控 (WebSocket)
// ============================================

/**
 * 功能: 连接实时视频流 → 接收检测结果 → 显示违规告警
 *
 * 需要安装: npm install socket.io-client
 *
 * WebSocket 事件:
 * - connect: 连接成功
 * - frame: 接收每一帧的检测结果
 * - violation: 接收违规事件
 * - signal_update: 接收信号灯状态更新
 */
import { useEffect, useState } from 'react';
import { io, Socket } from 'socket.io-client';

export const SimpleRealtimeMonitor = () => {
  const [socket, setSocket] = useState<Socket | null>(null);
  const [connected, setConnected] = useState(false);
  const [currentFrame, setCurrentFrame] = useState<string>('');
  const [violations, setViolations] = useState<any[]>([]);

  useEffect(() => {
    // 连接 WebSocket
    const newSocket = io('http://localhost:5000', {
      transports: ['websocket']
    });

    // 监听连接成功
    newSocket.on('connect', () => {
      console.log('✅ WebSocket 已连接');
      setConnected(true);

      // 订阅任务 (taskId 需要先通过 API 创建)
      newSocket.emit('subscribe', { taskId: 'demo_task_001' });
    });

    // 监听断开
    newSocket.on('disconnect', () => {
      console.log('❌ WebSocket 已断开');
      setConnected(false);
    });

    // 接收实时视频帧
    newSocket.on('frame', (data: any) => {
      console.log('📸 收到新帧:', data.frameNumber);
      setCurrentFrame(data.image); // base64 图片
    });

    // 接收违规告警
    newSocket.on('violation', (data: any) => {
      console.log('🚨 检测到违规:', data.violation);
      setViolations(prev => [data.violation, ...prev].slice(0, 10)); // 保留最近10条
    });

    setSocket(newSocket);

    // 组件卸载时断开连接
    return () => {
      newSocket.disconnect();
    };
  }, []);

  return (
    <div style={{ padding: '20px' }}>
      <h2>实时监控</h2>

      {/* 连接状态 */}
      <div style={{
        padding: '10px',
        background: connected ? '#4caf50' : '#f44336',
        color: 'white',
        marginBottom: '10px'
      }}>
        {connected ? '✅ 已连接' : '❌ 未连接'}
      </div>

      {/* 实时画面 */}
      <div style={{ marginBottom: '20px' }}>
        <h3>实时画面</h3>
        {currentFrame ? (
          <img
            src={`data:image/jpeg;base64,${currentFrame}`}
            alt="实时监控"
            style={{ maxWidth: '100%', border: '2px solid #333' }}
          />
        ) : (
          <div style={{
            width: '640px',
            height: '360px',
            background: '#eee',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            等待视频流...
          </div>
        )}
      </div>

      {/* 违规列表 */}
      <div>
        <h3>实时违规告警 ({violations.length})</h3>
        {violations.map((v, index) => (
          <div key={index} style={{
            padding: '10px',
            margin: '5px 0',
            background: '#ffebee',
            borderLeft: '4px solid #f44336'
          }}>
            <strong>{v.type}</strong> |
            方向: {v.direction}
            {v.confidence !== undefined && ` | 置信度: ${(v.confidence * 100).toFixed(0)}%`}
          </div>
        ))}
      </div>
    </div>
  );
};


// ============================================
// 📊 示例 3: API 数据获取示例
// ============================================

/**
 * 演示如何调用各种 API 接口
 */
export const APIExamples = () => {
  const API_BASE = 'http://localhost:5000';

  // 示例: 获取所有违规记录
  const fetchViolations = async () => {
    const response = await fetch(`${API_BASE}/violations`);
    const data = await response.json();
    console.log('违规记录:', data.violations);
  };

  // 示例: 启动实时检测任务
  const startRealtimeTask = async () => {
    const response = await fetch(`${API_BASE}/start-realtime`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        taskId: `task_${Date.now()}`,
        videoUrl: 'path/to/video.mp4',
        intersectionId: 1,
        direction: 'SOUTH'
      })
    });
    const data = await response.json();
    console.log('任务已启动:', data);
  };

  // 示例: 获取信号灯状态
  const getSignalStatus = async () => {
    const response = await fetch(`${API_BASE}/signal-status/1`);
    const data = await response.json();
    console.log('信号灯状态:', data);
  };

  return (
    <div style={{ padding: '20px' }}>
      <h2>API 调用示例</h2>

      <button onClick={fetchViolations} style={{ margin: '5px' }}>
        获取违规记录
      </button>

      <button onClick={startRealtimeTask} style={{ margin: '5px' }}>
        启动实时检测
      </button>

      <button onClick={getSignalStatus} style={{ margin: '5px' }}>
        获取信号灯状态
      </button>

      <div style={{ marginTop: '20px', padding: '10px', background: '#f5f5f5' }}>
        <p>打开浏览器控制台查看结果</p>
      </div>
    </div>
  );
};


// ============================================
// 💡 完整示例: 组合使用
// ============================================

export const QuickStartDemo = () => {
  const [activeTab, setActiveTab] = useState<'image' | 'realtime' | 'api'>('image');

  return (
    <div style={{ fontFamily: 'Arial, sans-serif' }}>
      <div style={{
        background: '#2196f3',
        color: 'white',
        padding: '20px',
        marginBottom: '20px'
      }}>
        <h1 style={{ margin: 0 }}>AI 交通检测 - 快速入门</h1>
        <p style={{ margin: '10px 0 0 0' }}>
          后端 API: http://localhost:5000
        </p>
      </div>

      {/* 标签切换 */}
      <div style={{ borderBottom: '2px solid #ddd', marginBottom: '20px' }}>
        <button
          onClick={() => setActiveTab('image')}
          style={{
            padding: '10px 20px',
            border: 'none',
            background: activeTab === 'image' ? '#2196f3' : '#fff',
            color: activeTab === 'image' ? '#fff' : '#000',
            cursor: 'pointer'
          }}
        >
          图片检测
        </button>
        <button
          onClick={() => setActiveTab('realtime')}
          style={{
            padding: '10px 20px',
            border: 'none',
            background: activeTab === 'realtime' ? '#2196f3' : '#fff',
            color: activeTab === 'realtime' ? '#fff' : '#000',
            cursor: 'pointer'
          }}
        >
          实时监控
        </button>
        <button
          onClick={() => setActiveTab('api')}
          style={{
            padding: '10px 20px',
            border: 'none',
            background: activeTab === 'api' ? '#2196f3' : '#fff',
            color: activeTab === 'api' ? '#fff' : '#000',
            cursor: 'pointer'
          }}
        >
          API 示例
        </button>
      </div>

      {/* 内容区域 */}
      {activeTab === 'image' && <SimpleImageDetector />}
      {activeTab === 'realtime' && <SimpleRealtimeMonitor />}
      {activeTab === 'api' && <APIExamples />}
    </div>
  );
};

export default QuickStartDemo;
