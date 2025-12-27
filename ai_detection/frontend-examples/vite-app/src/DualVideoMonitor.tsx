/**
 * 双视频窗口监控组件 - 南北/东西方向同时检测
 *
 * 功能：
 * - 两个并排的视频窗口
 * - 使用同一个视频源
 * - 分别使用 rois.json 和 rois2.json 进行检测
 * - 各自显示对应方向的违规记录
 *
 * 使用场景：
 * - 单个摄像头同时监控多个方向
 * - 南北向和东西向需要不同的检测参数
 */

import React, { useState, useEffect, useCallback } from 'react';
import { io, Socket } from 'socket.io-client';

// ==================== 类型定义 ====================

interface Violation {
  id: string;
  type: string;
  track_id: number;
  direction: string;
  confidence?: number;
  timestamp: string;
  screenshotUrl?: string;
}

interface SignalLights {
  north_bound: string;
  south_bound: string;
  west_bound: string;
  east_bound: string;
}

interface VideoWindowState {
  taskId: string;
  connected: boolean;
  currentFrame: string;
  violations: Violation[];
  progress: number;
  status: 'idle' | 'starting' | 'processing' | 'completed' | 'error';
}

// ==================== 单个视频窗口组件 ====================

interface SingleVideoWindowProps {
  title: string;
  roisConfig: string;
  videoFile: File | null;
  signalLights: SignalLights;
  leftTurnLights: SignalLights;
  apiBase: string;
  onTaskStarted?: (taskId: string) => void;
}

const SingleVideoWindow: React.FC<SingleVideoWindowProps> = ({
  title,
  roisConfig,
  videoFile,
  signalLights,
  leftTurnLights,
  apiBase,
  onTaskStarted
}) => {
  const [state, setState] = useState<VideoWindowState>({
    taskId: '',
    connected: false,
    currentFrame: '',
    violations: [],
    progress: 0,
    status: 'idle'
  });
  const [socket, setSocket] = useState<Socket | null>(null);
  const [uploading, setUploading] = useState(false);

  // 启动检测任务
  const startDetection = useCallback(async () => {
    if (!videoFile) return;

    setUploading(true);
    setState(prev => ({ ...prev, status: 'starting' }));

    const newTaskId = `${roisConfig.replace('.json', '')}_${Date.now()}`;

    try {
      const formData = new FormData();
      formData.append('video', videoFile);
      formData.append('taskId', newTaskId);
      formData.append('intersectionId', '1');
      formData.append('direction', 'SOUTH');
      formData.append('roisConfig', roisConfig);  // 指定 ROI 配置

      const response = await fetch(`${apiBase}/upload-video`, {
        method: 'POST',
        body: formData,
      });

      if (response.ok) {
        const data = await response.json();
        console.log(`✅ [${title}] 任务已启动:`, data);

        setState(prev => ({
          ...prev,
          taskId: newTaskId,
          status: 'processing'
        }));

        onTaskStarted?.(newTaskId);

        // 连接 WebSocket
        connectWebSocket(newTaskId);
      } else {
        setState(prev => ({ ...prev, status: 'error' }));
        alert(`[${title}] 视频上传失败`);
      }
    } catch (error) {
      console.error(`[${title}] 启动任务失败:`, error);
      setState(prev => ({ ...prev, status: 'error' }));
    } finally {
      setUploading(false);
    }
  }, [videoFile, roisConfig, title, apiBase, onTaskStarted]);

  // 连接 WebSocket
  const connectWebSocket = useCallback((taskId: string) => {
    const newSocket = io(apiBase, {
      transports: ['websocket']
    });

    newSocket.on('connect', () => {
      console.log(`✅ [${title}] WebSocket 已连接`);
      setState(prev => ({ ...prev, connected: true }));
      newSocket.emit('subscribe', { taskId });
    });

    newSocket.on('disconnect', () => {
      console.log(`❌ [${title}] WebSocket 已断开`);
      setState(prev => ({ ...prev, connected: false }));
    });

    newSocket.on('frame', (data: any) => {
      setState(prev => ({
        ...prev,
        currentFrame: data.image,
        progress: data.progress || prev.progress
      }));
    });

    newSocket.on('violation', (data: any) => {
      console.log(`🚨 [${title}] 检测到违规:`, data.violation);
      setState(prev => ({
        ...prev,
        violations: [data.violation, ...prev.violations].slice(0, 20)
      }));
    });

    newSocket.on('complete', () => {
      console.log(`✅ [${title}] 处理完成`);
      setState(prev => ({ ...prev, status: 'completed' }));
    });

    newSocket.on('error', (error: any) => {
      console.error(`❌ [${title}] 错误:`, error);
      setState(prev => ({ ...prev, status: 'error' }));
    });

    setSocket(newSocket);
  }, [title, apiBase]);

  // 组件卸载时断开连接
  useEffect(() => {
    return () => {
      if (socket) {
        socket.disconnect();
      }
    };
  }, [socket]);

  // 获取状态颜色
  const getStatusColor = () => {
    switch (state.status) {
      case 'processing': return state.connected ? '#4caf50' : '#ff9800';
      case 'completed': return '#2196f3';
      case 'error': return '#f44336';
      default: return '#9e9e9e';
    }
  };

  // 获取状态文字
  const getStatusText = () => {
    switch (state.status) {
      case 'idle': return '待启动';
      case 'starting': return '启动中...';
      case 'processing': return state.connected ? `处理中 ${state.progress.toFixed(1)}%` : '连接中...';
      case 'completed': return '已完成';
      case 'error': return '错误';
      default: return '未知';
    }
  };

  return (
    <div style={{
      flex: 1,
      minWidth: '400px',
      border: '2px solid #333',
      borderRadius: '10px',
      overflow: 'hidden',
      background: '#1a1a2e',
    }}>
      {/* 标题栏 */}
      <div style={{
        background: '#16213e',
        padding: '10px 15px',
        borderBottom: '1px solid #333',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center'
      }}>
        <h3 style={{ margin: 0, color: '#fff', fontSize: '16px' }}>
          {title}
        </h3>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span style={{
            padding: '4px 12px',
            borderRadius: '15px',
            fontSize: '12px',
            background: getStatusColor(),
            color: '#fff'
          }}>
            {getStatusText()}
          </span>
          <span style={{ color: '#888', fontSize: '11px' }}>
            {roisConfig}
          </span>
        </div>
      </div>

      {/* 信号灯面板 */}
      <div style={{
        padding: '10px 15px',
        borderBottom: '1px solid #333',
        display: 'flex',
        justifyContent: 'center',
        gap: '15px'
      }}>
        {['north_bound', 'south_bound', 'east_bound', 'west_bound'].map((dir) => {
          const straight = signalLights[dir as keyof SignalLights] || 'red';
          const leftTurn = leftTurnLights[dir as keyof SignalLights] || 'red';
          const label = dir.replace('_bound', '').toUpperCase().charAt(0);

          return (
            <div key={dir} style={{ textAlign: 'center' }}>
              <div style={{ color: '#888', fontSize: '10px', marginBottom: '3px' }}>
                {label}
              </div>
              <div style={{
                display: 'flex',
                gap: '3px',
                alignItems: 'center'
              }}>
                {/* 直行灯 */}
                <div style={{
                  width: '16px',
                  height: '16px',
                  borderRadius: '50%',
                  background: straight === 'green' ? '#00ff00' :
                             straight === 'yellow' ? '#ffff00' : '#ff0000',
                  boxShadow: straight !== 'red' ? `0 0 8px ${straight === 'green' ? '#00ff00' : '#ffff00'}` : 'none',
                  border: '1px solid #444'
                }} />
                {/* 左转灯 */}
                <div style={{
                  width: '14px',
                  height: '14px',
                  borderRadius: '50%',
                  background: leftTurn === 'green' ? '#00ff00' :
                             leftTurn === 'yellow' ? '#ffff00' : '#ff0000',
                  boxShadow: leftTurn !== 'red' ? `0 0 6px ${leftTurn === 'green' ? '#00ff00' : '#ffff00'}` : 'none',
                  border: '1px solid #444',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontSize: '8px',
                  color: leftTurn === 'red' ? '#666' : '#000'
                }}>
                  ←
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* 视频画面 */}
      <div style={{
        height: '300px',
        background: '#0f0f23',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        position: 'relative'
      }}>
        {state.currentFrame ? (
          <img
            src={`data:image/jpeg;base64,${state.currentFrame}`}
            alt="实时监控"
            style={{
              maxWidth: '100%',
              maxHeight: '100%',
              objectFit: 'contain'
            }}
          />
        ) : (
          <div style={{
            color: '#666',
            textAlign: 'center'
          }}>
            {state.status === 'idle' ? (
              <>
                <div style={{ fontSize: '40px', marginBottom: '10px' }}>📹</div>
                <div>等待启动检测</div>
              </>
            ) : (
              <>
                <div style={{ fontSize: '40px', marginBottom: '10px' }}>⏳</div>
                <div>加载中...</div>
              </>
            )}
          </div>
        )}

        {/* 启动按钮 */}
        {state.status === 'idle' && videoFile && (
          <button
            onClick={startDetection}
            disabled={uploading}
            style={{
              position: 'absolute',
              bottom: '20px',
              left: '50%',
              transform: 'translateX(-50%)',
              padding: '10px 25px',
              background: uploading ? '#666' : '#4caf50',
              color: '#fff',
              border: 'none',
              borderRadius: '25px',
              cursor: uploading ? 'not-allowed' : 'pointer',
              fontSize: '14px',
              fontWeight: 'bold',
              boxShadow: '0 4px 15px rgba(76, 175, 80, 0.4)'
            }}
          >
            {uploading ? '启动中...' : '▶ 开始检测'}
          </button>
        )}
      </div>

      {/* 违规记录 */}
      <div style={{
        maxHeight: '200px',
        overflowY: 'auto',
        borderTop: '1px solid #333'
      }}>
        <div style={{
          padding: '8px 15px',
          background: '#16213e',
          position: 'sticky',
          top: 0,
          borderBottom: '1px solid #333',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <span style={{ color: '#fff', fontSize: '13px', fontWeight: 'bold' }}>
            🚨 违规记录
          </span>
          <span style={{
            background: state.violations.length > 0 ? '#f44336' : '#4caf50',
            color: '#fff',
            padding: '2px 8px',
            borderRadius: '10px',
            fontSize: '11px'
          }}>
            {state.violations.length}
          </span>
        </div>

        {state.violations.length > 0 ? (
          state.violations.map((v, index) => (
            <div
              key={v.id || index}
              style={{
                padding: '10px 15px',
                borderBottom: '1px solid #2a2a4a',
                background: index % 2 === 0 ? '#1a1a2e' : '#16213e'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ color: '#ff9800', fontWeight: 'bold', fontSize: '13px' }}>
                  {v.type === 'red_light_running' ? '🚦 闯红灯' :
                   v.type === 'wrong_way_driving' ? '⬅️ 逆行' :
                   v.type === 'lane_change_across_solid_line' ? '〰️ 压线' :
                   v.type === 'waiting_area_red_entry' ? '🔴 待转区红灯进入' :
                   v.type === 'waiting_area_illegal_exit' ? '⚠️ 待转区非法驶离' :
                   v.type}
                </span>
                <span style={{ color: '#888', fontSize: '11px' }}>
                  {v.direction} | ID:{v.track_id}
                </span>
              </div>
              {v.confidence !== undefined && (
                <div style={{ color: '#666', fontSize: '11px', marginTop: '3px' }}>
                  置信度: {(v.confidence * 100).toFixed(1)}%
                </div>
              )}
            </div>
          ))
        ) : (
          <div style={{
            padding: '30px',
            textAlign: 'center',
            color: '#666'
          }}>
            暂无违规记录
          </div>
        )}
      </div>
    </div>
  );
};

// ==================== 双视频窗口主组件 ====================

export const DualVideoMonitor: React.FC = () => {
  const API_BASE = 'http://localhost:5000';

  // 共享状态
  const [videoFile, setVideoFile] = useState<File | null>(null);
  const [signalLights, setSignalLights] = useState<SignalLights>({
    north_bound: 'red',
    south_bound: 'red',
    west_bound: 'red',
    east_bound: 'red'
  });
  const [leftTurnLights, setLeftTurnLights] = useState<SignalLights>({
    north_bound: 'red',
    south_bound: 'red',
    west_bound: 'red',
    east_bound: 'red'
  });
  const [globalSocket, setGlobalSocket] = useState<Socket | null>(null);

  // 连接全局 WebSocket 接收信号灯状态
  useEffect(() => {
    const socket = io(API_BASE, {
      transports: ['websocket']
    });

    socket.on('connect', () => {
      console.log('✅ 全局 WebSocket 已连接（信号灯同步）');
    });

    socket.on('traffic', (data: any) => {
      console.log('🚦 收到信号灯状态:', data);
      if (data.signals) {
        setSignalLights(prev => ({ ...prev, ...data.signals }));
      }
      if (data.leftTurnSignals) {
        setLeftTurnLights(prev => ({ ...prev, ...data.leftTurnSignals }));
      }
    });

    setGlobalSocket(socket);

    return () => {
      socket.disconnect();
    };
  }, []);

  return (
    <div style={{
      padding: '20px',
      background: '#0f0f23',
      minHeight: '100vh',
      fontFamily: 'Arial, sans-serif'
    }}>
      {/* 标题 */}
      <div style={{
        textAlign: 'center',
        marginBottom: '20px'
      }}>
        <h1 style={{
          color: '#fff',
          margin: '0 0 10px 0',
          fontSize: '24px'
        }}>
          🚦 双向视频监控系统
        </h1>
        <p style={{ color: '#888', margin: 0, fontSize: '14px' }}>
          同一视频源 · 双 ROI 配置 · 多方向检测
        </p>
      </div>

      {/* 视频选择 */}
      <div style={{
        background: '#16213e',
        borderRadius: '10px',
        padding: '20px',
        marginBottom: '20px',
        textAlign: 'center'
      }}>
        <h3 style={{ color: '#fff', margin: '0 0 15px 0', fontSize: '16px' }}>
          📹 选择视频文件
        </h3>
        <input
          type="file"
          accept="video/*"
          onChange={(e) => setVideoFile(e.target.files?.[0] || null)}
          style={{
            padding: '10px',
            background: '#1a1a2e',
            border: '2px dashed #444',
            borderRadius: '8px',
            color: '#fff',
            cursor: 'pointer'
          }}
        />
        {videoFile && (
          <div style={{ marginTop: '10px', color: '#4caf50' }}>
            ✅ 已选择: {videoFile.name} ({(videoFile.size / 1024 / 1024).toFixed(2)} MB)
          </div>
        )}
        <p style={{ color: '#888', margin: '10px 0 0 0', fontSize: '12px' }}>
          选择视频后，分别点击两个窗口的"开始检测"按钮启动各方向检测
        </p>
      </div>

      {/* 双视频窗口 */}
      <div style={{
        display: 'flex',
        gap: '20px',
        flexWrap: 'wrap'
      }}>
        {/* 窗口 1: 使用 rois.json - 原始方向 */}
        <SingleVideoWindow
          title="📍 视角 1 - 南北向检测"
          roisConfig="rois.json"
          videoFile={videoFile}
          signalLights={signalLights}
          leftTurnLights={leftTurnLights}
          apiBase={API_BASE}
        />

        {/* 窗口 2: 使用 rois2.json - 旋转方向 */}
        <SingleVideoWindow
          title="📍 视角 2 - 东西向检测"
          roisConfig="rois2.json"
          videoFile={videoFile}
          signalLights={signalLights}
          leftTurnLights={leftTurnLights}
          apiBase={API_BASE}
        />
      </div>

      {/* 说明 */}
      <div style={{
        marginTop: '20px',
        padding: '15px 20px',
        background: '#16213e',
        borderRadius: '10px',
        color: '#888',
        fontSize: '13px'
      }}>
        <h4 style={{ color: '#fff', margin: '0 0 10px 0' }}>📖 使用说明</h4>
        <ul style={{ margin: 0, paddingLeft: '20px' }}>
          <li><strong>rois.json</strong>: 原始方向配置 (north_bound, south_bound, east_bound, west_bound)</li>
          <li><strong>rois2.json</strong>: 顺时针旋转90°配置，用于从不同视角检测同一路口</li>
          <li>两个窗口使用同一视频源，但各自独立处理和检测</li>
          <li>信号灯状态全局同步，所有窗口共享同一信号灯数据</li>
        </ul>
      </div>
    </div>
  );
};

export default DualVideoMonitor;

