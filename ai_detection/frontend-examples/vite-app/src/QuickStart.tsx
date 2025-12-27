/**
 * eastAI 交通检测 - 快速入门示例
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
      console.log('🔍 检测结果:', data);
      console.log('📸 是否有标注图片:', !!data.annotated_image);
      if (data.annotated_image) {
        console.log('east标注图片长度:', data.annotated_image.length);
      }
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
          {result.annotated_image ? (
            <div style={{ marginTop: '15px' }}>
              <h4>标注结果图片：</h4>
              <img
                src={`data:image/jpeg;base64,${result.annotated_image}`}
                alt="检测结果"
                style={{
                  maxWidth: '100%',
                  border: '2px solid #4caf50',
                  borderRadius: '5px',
                  marginTop: '10px'
                }}
              />
            </div>
          ) : (
            <div style={{
              padding: '20px',
              background: '#fff3e0',
              border: '1px dashed #ff9800',
              marginTop: '15px',
              borderRadius: '5px'
            }}>
              ⚠️ 未返回标注图片，请检查后端是否正常
            </div>
          )}

          {/* 违规详情 */}
          {result.violations?.map((v: any, index: number) => (
            <div key={index} style={{
              padding: '15px',
              margin: '10px 0',
              background: '#fff3e0',
              borderLeft: '5px solid #ff9800',
              borderRadius: '3px'
            }}>
              <strong style={{ color: '#f44336', fontSize: '16px' }}>
                {v.type === 'red_light_running' ? '🚦 闯红灯' :
                 v.type === 'wrong_way_driving' ? '⬅️ 逆行' :
                 v.type === 'lane_change_across_solid_line' ? '〰️ 压线变道' :
                 v.type === 'waiting_area_red_entry' ? '🔴 待转区红灯进入' :
                 v.type === 'waiting_area_illegal_exit' ? '⚠️ 待转区非法驶离' :
                 v.type}
              </strong>
              <br />
              <strong>方向:</strong> {v.direction} <br />
              {v.confidence !== undefined && (
                <><strong>置信度:</strong> {(v.confidence * 100).toFixed(1)}% <br /></>
              )}
              {v.screenshotUrl && (
                <div style={{ marginTop: '10px' }}>
                  <strong>违规快照:</strong><br />
                  <img
                    src={v.screenshotUrl}
                    alt="违规快照"
                    style={{
                      maxWidth: '300px',
                      marginTop: '5px',
                      border: '2px solid #f44336',
                      borderRadius: '3px'
                    }}
                  />
                </div>
              )}
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
import { useEffect } from 'react';
import { io, Socket } from 'socket.io-client';

export const SimpleRealtimeMonitor = () => {
  const API_BASE = 'http://localhost:5000';

  const [socket, setSocket] = useState<Socket | null>(null);
  const [connected, setConnected] = useState(false);
  const [currentFrame, setCurrentFrame] = useState<string>('');
  const [violations, setViolations] = useState<any[]>([]);
  const [taskId, setTaskId] = useState<string>('');

  // 新增：视频上传相关状态
  const [videoFile, setVideoFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [taskStarted, setTaskStarted] = useState(false);

  // 信号灯状态（直行灯）
  const [signalLights, setSignalLights] = useState<{
    north_bound: string;
    south_bound: string;
    west_bound: string;
    east_bound: string;
  }>({
    north_bound: 'red',
    south_bound: 'red',
    west_bound: 'red',
    east_bound: 'red'
  });

  // 左转灯状态
  const [leftTurnLights, setLeftTurnLights] = useState<{
    north_bound: string;
    south_bound: string;
    west_bound: string;
    east_bound: string;
  }>({
    north_bound: 'red',
    south_bound: 'red',
    west_bound: 'red',
    east_bound: 'red'
  });

  // 信号灯数据源模式 ('simulation' = Java后端, 'circle' = 自动循环)
  const [signalSourceMode, setSignalSourceMode] = useState<'backend' | 'simulation'>('backend');

  // 切换信号灯数据源
  const toggleSignalSource = async () => {
    const newMode = signalSourceMode === 'backend' ? 'simulation' : 'backend';

    try {
      const response = await fetch(`${API_BASE}/api/traffic/signal-source-mode`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ mode: newMode })
      });

      const data = await response.json();
      if (data.success) {
        setSignalSourceMode(newMode);
        console.log(`信号灯数据源已切换到: ${newMode}`);
      }
    } catch (error) {
      console.error('切换信号源失败:', error);
    }
  };

  // 处理视频上传和启动任务
  const handleStartDetection = async () => {
    if (!videoFile) {
      alert('请先选择视频文件');
      return;
    }

    setUploading(true);
    const newTaskId = `task_${Date.now()}`;

    try {
      // 方式1: 如果后端支持视频上传
      const formData = new FormData();
      formData.append('video', videoFile);
      formData.append('taskId', newTaskId);
      formData.append('intersectionId', '1');
      formData.append('direction', 'SOUTH');

      const response = await fetch('http://localhost:5000/upload-video', {
        method: 'POST',
        body: formData,
      });

      if (response.ok) {
        const data = await response.json();
        setTaskId(newTaskId);
        setTaskStarted(true);
        console.log('east任务已启动:', data);

        // 启动任务后连接 WebSocket
        connectWebSocket(newTaskId);
      } else {
        alert('视频上传失败，请检查后端是否支持 /upload-video 接口');
      }
    } catch (error) {
      console.error('启动任务失败:', error);
      alert('启动失败: ' + error);
    } finally {
      setUploading(false);
    }
  };

  // 连接 WebSocket
  const connectWebSocket = (taskId: string) => {
    const newSocket = io('http://localhost:5000', {
      transports: ['websocket']
    });

    // 监听连接成功
    newSocket.on('connect', () => {
      console.log('eastWebSocket 已连接');
      setConnected(true);
      newSocket.emit('subscribe', { taskId });
    });

    // 监听断开
    newSocket.on('disconnect', () => {
      console.log('❌ WebSocket 已断开');
      setConnected(false);
    });

    // 接收实时视频帧
    newSocket.on('frame', (data: any) => {
      console.log('📸 收到新帧:', data.frameNumber);
      setCurrentFrame(data.image);
      if (data.progress) {
        setUploadProgress(data.progress);
      }
    });

    // 接收违规告警
    newSocket.on('violation', (data: any) => {
      console.log('🚨 检测到违规:', data.violation);
      setViolations(prev => [data.violation, ...prev].slice(0, 10));
    });

    // 接收信号灯状态更新
    newSocket.on('traffic', (data: any) => {
      console.log('🚦 收到信号灯状态:', data);
      if (data && typeof data === 'object') {
        // 更新直行灯状态
        if (data.signals || data.north_bound !== undefined) {
          const signals = data.signals || data;
          setSignalLights(prev => ({
            ...prev,
            ...signals
          }));
        }
        // 更新左转灯状态
        if (data.leftTurnSignals) {
          setLeftTurnLights(prev => ({
            ...prev,
            ...data.leftTurnSignals
          }));
        }
      }
    });

    // 任务完成
    newSocket.on('complete', () => { 
      console.log('视频处理完成！');
    });

    setSocket(newSocket);
  };

  // 组件卸载时断开连接
  useEffect(() => {
    return () => {
      if (socket) {
        socket.disconnect();
      }
    };
  }, [socket]);

  return (
    <div style={{ padding: '20px', maxWidth: '1200px' }}>
      <h2>视频实时检测</h2>

      {/* 步骤 1: 上传视频 */}
      {!taskStarted && (
        <div style={{
          padding: '20px',
          background: '#f5f5f5',
          borderRadius: '8px',
          marginBottom: '20px'
        }}>
          <h3>📹 步骤 1: 选择视频文件</h3>
          <input
            type="file"
            accept="video/*"
            onChange={(e) => setVideoFile(e.target.files?.[0] || null)}
            style={{ marginBottom: '10px' }}
          />

          {videoFile && (
            <div style={{ marginTop: '10px', color: '#666' }}>
              <p>east已选择: {videoFile.name}</p>
              <p>大小: {(videoFile.size / 1024 / 1024).toFixed(2)} MB</p>
            </div>
          )}

          <button
            onClick={handleStartDetection}
            disabled={!videoFile || uploading}
            style={{
              marginTop: '10px',
              padding: '10px 20px',
              background: videoFile && !uploading ? '#4caf50' : '#ccc',
              color: 'white',
              border: 'none',
              borderRadius: '5px',
              cursor: videoFile && !uploading ? 'pointer' : 'not-allowed',
              fontSize: '16px'
            }}
          >
            {uploading ? '上传中...' : 'east开始检测'}
          </button>
        </div>
      )}

      {/* 步骤 2: 连接状态 */}
      {taskStarted && (
        <div style={{
          padding: '10px 20px',
          background: connected ? '#4caf50' : '#f44336',
          color: 'white',
          marginBottom: '20px',
          borderRadius: '5px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <span>{connected ? 'east已连接' : '⏳ 连接中...'}</span>
          {uploadProgress > 0 && (
            <span>处理进度: {uploadProgress.toFixed(1)}%</span>
          )}
          {taskId && <span>任务ID: {taskId}</span>}
        </div>
      )}

      {/* 红绿灯状态 - 始终显示 */}
      <div style={{ marginBottom: '20px' }}>
        <div style={{ display: 'flex', gap: '15px', alignItems: 'center', marginBottom: '10px' }}>
          <button
            onClick={toggleSignalSource}
            style={{
              padding: '8px 16px',
              background: signalSourceMode === 'backend' ? '#2196f3' : '#ff9800',
              color: 'white',
              border: 'none',
              borderRadius: '5px',
              cursor: 'pointer',
              fontSize: '14px',
              fontWeight: 'bold'
            }}
          >
            {signalSourceMode === 'backend' ? '🌐 Java后端数据' : '🔄 自动循环模拟'}
          </button>
          <span style={{ fontSize: '12px', color: '#666' }}>
            当前数据源: {signalSourceMode === 'backend' ? 'Java后端' : '自动循环模拟'}
          </span>
        </div>
        <SignalLightsPanel signalLights={signalLights} leftTurnLights={leftTurnLights} />
      </div>

      {/* 步骤 3: 实时画面 */}
      <div style={{ marginBottom: '20px' }}>
        <h3>📺 实时画面</h3>
        {currentFrame ? (
          <img
            src={`data:image/jpeg;base64,${currentFrame}`}
            alt="实时监控"
            style={{
              maxWidth: '100%',
              border: '2px solid #333',
              borderRadius: '5px'
            }}
          />
        ) : (
          <div style={{
            width: '100%',
            maxWidth: '800px',
            height: '450px',
            background: '#eee',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: '5px',
            color: '#999',
            fontSize: '18px'
          }}>
            {taskStarted ? '⏳ 等待视频流...' : '👆 请先上传视频并开始检测'}
          </div>
        )}
      </div>

      {/* 步骤 4: 违规列表 */}
      <div>
        <h3>🚨 实时违规告警 ({violations.length})</h3>
        {violations.length > 0 ? (
          violations.map((v, index) => (
            <div key={index} style={{
              padding: '15px',
              margin: '10px 0',
              background: '#fff3e0',
              borderLeft: '5px solid #ff9800',
              borderRadius: '3px',
              boxShadow: '0 2px 4px rgba(0,0,0,0.1)'
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <strong style={{
                    color: '#f44336',
                    fontSize: '16px',
                    marginRight: '10px'
                  }}>
                    {v.type === 'red_light_running' || v.type === 'red_light' ? '🚦 闯红灯' :
                     v.type === 'wrong_way_driving' || v.type === 'wrong_way' ? '⬅️ 逆行' :
                     v.type === 'lane_change_across_solid_line' || v.type === 'lane_change' ? '〰️ 压线变道' :
                     v.type === 'waiting_area_red_entry' ? '🔴 待转区红灯进入' :
                     v.type === 'waiting_area_illegal_exit' ? '⚠️ 待转区非法驶离' :
                     '❓ ' + v.type}
                  </strong>
                  <span>方向: {v.direction}</span>
                  <span style={{ marginLeft: '15px' }}>
                    车辆ID: {v.track_id}
                  </span>
                </div>
                <div style={{ textAlign: 'right' }}>
                  {v.confidence !== undefined && (
                    <div>置信度: {(v.confidence * 100).toFixed(1)}%</div>
                  )}
                </div>
              </div>
            </div>
          ))
        ) : (
          <p style={{ color: '#999', padding: '20px', textAlign: 'center' }}>
            暂无违规记录
          </p>
        )}
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

  // 示例: 获取信号灯数据源模式
  const getSignalStatus = async () => {
    const response = await fetch(`${API_BASE}/api/traffic/signal-source-mode`);
    const data = await response.json();
    console.log('信号灯数据源模式:', data);
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
// 🚦 红绿灯状态面板组件
// ============================================

interface SignalLightsPanelProps {
  signalLights: {
    north_bound: string;
    south_bound: string;
    west_bound: string;
    east_bound: string;
  };
  leftTurnLights: {
    north_bound: string;
    south_bound: string;
    west_bound: string;
    east_bound: string;
  };
}

const SignalLightsPanel: React.FC<SignalLightsPanelProps> = ({ signalLights, leftTurnLights }) => {
  const directionConfig = [
    { key: 'north_bound', label: '北向', short: 'N' },
    { key: 'south_bound', label: '南向', short: 'S' },
    { key: 'west_bound', label: '西向', short: 'W' },
    { key: 'east_bound', label: '东向', short: 'E' },
  ];

  const getLightStyle = (state: string) => {
    const baseStyle: React.CSSProperties = {
      width: '30px',
      height: '30px',
      borderRadius: '50%',
      margin: '3px auto',
      border: '2px solid #444',
      transition: 'all 0.3s ease',
    };

    let activeStyle: React.CSSProperties = {};
    if (state === 'green') {
      activeStyle = {
        background: '#00ff00',
        boxShadow: '0 0 20px #00ff00, 0 0 40px #00ff00',
      };
    } else if (state === 'red') {
      activeStyle = {
        background: '#ff0000',
        boxShadow: '0 0 20px #ff0000, 0 0 40px #ff0000',
      };
    } else if (state === 'yellow') {
      activeStyle = {
        background: '#ffff00',
        boxShadow: '0 0 20px #ffff00, 0 0 40px #ffff00',
      };
    }

    return { ...baseStyle, ...activeStyle };
  };

  return (
    <div style={{
      background: '#1a1a2e',
      borderRadius: '10px',
      padding: '15px 20px',
      marginBottom: '20px',
      display: 'inline-block',
    }}>
      <h4 style={{ margin: '0 0 10px 0', color: '#fff', fontSize: '14px' }}>
        🚦 信号灯状态
      </h4>
      <div style={{ display: 'flex', gap: '20px' }}>
        {directionConfig.map((dir) => {
          const state = signalLights[dir.key as keyof typeof signalLights] || 'red';
          const leftTurnState = leftTurnLights[dir.key as keyof typeof leftTurnLights] || 'red';

          return (
            <div key={dir.key} style={{ textAlign: 'center' }}>
              <div style={{ color: '#888', fontSize: '12px', marginBottom: '5px' }}>
                {dir.label}
              </div>
              {/* 直行灯 */}
              <div style={getLightStyle(state)} />
              {/* 左转灯 */}
              <div style={{
                ...getLightStyle(leftTurnState),
                width: '22px',
                height: '22px',
                fontSize: '10px',
                lineHeight: '22px',
                color: leftTurnState === 'green' ? '#000' : leftTurnState === 'yellow' ? '#000' : '#444',
                fontWeight: 'bold',
                position: 'relative',
              }}>
                <span style={{
                  position: 'absolute',
                  top: '50%',
                  left: '50%',
                  transform: 'translate(-50%, -50%)'
                }}>
                  ←
                </span>
              </div>
            </div>
          );
        })}
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
