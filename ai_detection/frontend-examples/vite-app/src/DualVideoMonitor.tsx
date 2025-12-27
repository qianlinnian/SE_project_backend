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

import React, { useState, useEffect, useCallback, useRef, useImperativeHandle, forwardRef } from 'react';
import { io, Socket } from 'socket.io-client';

// ==================== 类型定义 ====================

interface Violation {
  id: string;
  type: string;
  track_id: number;
  direction: string;
  confidence?: number;
  timestamp: string;
  screenshotUrl?: string;      // 快照访问 URL
  screenshot?: string;          // 快照文件路径
  screenshot_base64?: string;   // Base64 编码的快照（如果有）
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
  sharedVideoFile?: File | null;  // 共享视频（可选）
  signalLights: SignalLights;
  leftTurnLights: SignalLights;
  apiBase: string;
  onTaskStarted?: (taskId: string) => void;
}

// 暴露给父组件的方法
export interface SingleVideoWindowRef {
  startDetection: () => Promise<void>;
  resetDetection: () => void;
  isIdle: () => boolean;
}

const SingleVideoWindow = forwardRef<SingleVideoWindowRef, SingleVideoWindowProps>(({
  title,
  roisConfig,
  sharedVideoFile,
  signalLights,
  leftTurnLights,
  apiBase,
  onTaskStarted
}, ref) => {
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
  
  // 每个窗口可以有自己的视频文件
  const [localVideoFile, setLocalVideoFile] = useState<File | null>(null);
  
  // 选中的违规记录（用于显示大图）
  const [selectedViolation, setSelectedViolation] = useState<Violation | null>(null);
  
  // 优先使用本地选择的视频，否则使用共享视频
  const videoFile = localVideoFile || sharedVideoFile || null;
  
  // 获取违规快照的 URL
  const getScreenshotUrl = (v: Violation): string | null => {
    // 优先使用 screenshotUrl
    if (v.screenshotUrl) return v.screenshotUrl;
    // 其次使用 screenshot 路径构建 URL
    if (v.screenshot) {
      const filename = v.screenshot.split(/[/\\]/).pop();
      return `${apiBase}/screenshots/${filename}`;
    }
    return null;
  };

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
        console.log(`[${title}] 任务已启动:`, data);

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
      console.log(`[${title}] WebSocket 已连接`);
      setState(prev => ({ ...prev, connected: true }));
      newSocket.emit('subscribe', { taskId });
    });

    newSocket.on('disconnect', () => {
      console.log(`❌ [${title}] WebSocket 已断开`);
      setState(prev => ({ ...prev, connected: false }));
    });

    newSocket.on('frame', (data: any) => {
      // 只处理属于当前任务的帧
      if (data.taskId !== taskId) return;
      
      setState(prev => ({
        ...prev,
        currentFrame: data.image,
        progress: data.progress || prev.progress
      }));
    });

    newSocket.on('violation', (data: any) => {
      // 只处理属于当前任务的违规
      if (data.taskId !== taskId) return;
      
      console.log(`🚨 [${title}] 检测到违规:`, data.violation);
      setState(prev => ({
        ...prev,
        violations: [data.violation, ...prev.violations].slice(0, 20)
      }));
    });

    newSocket.on('complete', (data: any) => {
      // 只处理属于当前任务的完成事件
      if (data?.taskId && data.taskId !== taskId) return;
      
      console.log(`[${title}] 处理完成`);
      setState(prev => ({ ...prev, status: 'completed' }));
    });

    newSocket.on('error', (error: any) => {
      // 只处理属于当前任务的错误
      if (error?.taskId && error.taskId !== taskId) return;
      
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

  // 重置检测状态
  const resetDetection = useCallback(() => {
    // 断开 WebSocket 连接
    if (socket) {
      socket.disconnect();
      setSocket(null);
    }
    
    // 重置状态
    setState({
      taskId: '',
      connected: false,
      currentFrame: '',
      violations: [],
      progress: 0,
      status: 'idle'
    });
    setUploading(false);
    
    console.log(`[${title}] 已重置`);
  }, [socket, title]);

  // 暴露方法给父组件
  useImperativeHandle(ref, () => ({
    startDetection,
    resetDetection,
    isIdle: () => state.status === 'idle'
  }), [startDetection, resetDetection, state.status]);

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

      {/* 视频选择区域 */}
      <div style={{
        padding: '8px 15px',
        borderBottom: '1px solid #333',
        background: '#12122a',
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        flexWrap: 'wrap'
      }}>
        <label style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          cursor: 'pointer',
          padding: '6px 12px',
          background: '#1a1a2e',
          borderRadius: '5px',
          border: '1px dashed #444',
          fontSize: '12px',
          color: '#888'
        }}>
          <span>📁</span>
          <span>{localVideoFile ? '更换视频' : '选择视频'}</span>
          <input
            type="file"
            accept="video/*"
            onChange={(e) => setLocalVideoFile(e.target.files?.[0] || null)}
            style={{ display: 'none' }}
          />
        </label>
        
        {videoFile && (
          <span style={{ 
            color: localVideoFile ? '#4caf50' : '#2196f3', 
            fontSize: '11px',
            flex: 1,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap'
          }}>
            {localVideoFile ? '📹 ' : '📂 共享: '}
            {videoFile.name}
          </span>
        )}

        {localVideoFile && (
          <button
            onClick={() => setLocalVideoFile(null)}
            style={{
              padding: '4px 8px',
              background: 'transparent',
              border: '1px solid #666',
              borderRadius: '3px',
              color: '#888',
              fontSize: '10px',
              cursor: 'pointer'
            }}
            title="使用共享视频"
          >
            ✕ 清除
          </button>
        )}
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

        {/* 按钮区域 */}
        <div style={{
          position: 'absolute',
          bottom: '15px',
          left: '50%',
          transform: 'translateX(-50%)',
          display: 'flex',
          gap: '10px'
        }}>
          {/* 启动按钮 - 仅在 idle 状态显示 */}
          {state.status === 'idle' && videoFile && (
            <button
              onClick={startDetection}
              disabled={uploading}
              style={{
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

          {/* 重新检测按钮 - 在完成或错误状态显示 */}
          {(state.status === 'completed' || state.status === 'error') && videoFile && (
            <>
              <button
                onClick={() => {
                  resetDetection();
                  // 延迟启动，确保状态已重置
                  setTimeout(() => startDetection(), 100);
                }}
                style={{
                  padding: '10px 20px',
                  background: 'linear-gradient(135deg, #ff9800, #f44336)',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '25px',
                  cursor: 'pointer',
                  fontSize: '13px',
                  fontWeight: 'bold',
                  boxShadow: '0 4px 15px rgba(255, 152, 0, 0.4)'
                }}
              >
                重新检测
              </button>
              <button
                onClick={resetDetection}
                style={{
                  padding: '10px 20px',
                  background: '#666',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '25px',
                  cursor: 'pointer',
                  fontSize: '13px',
                  fontWeight: 'bold'
                }}
              >
                ✕ 清空结果
              </button>
            </>
          )}

          {/* 停止按钮 - 在处理中状态显示 */}
          {state.status === 'processing' && (
            <button
              onClick={resetDetection}
              style={{
                padding: '10px 20px',
                background: '#f44336',
                color: '#fff',
                border: 'none',
                borderRadius: '25px',
                cursor: 'pointer',
                fontSize: '13px',
                fontWeight: 'bold',
                boxShadow: '0 4px 15px rgba(244, 67, 54, 0.4)'
              }}
            >
              ⏹ 停止检测
            </button>
          )}
        </div>
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
          state.violations.map((v, index) => {
            const screenshotUrl = getScreenshotUrl(v);
            
            return (
              <div
                key={v.id || index}
                style={{
                  padding: '10px 15px',
                  borderBottom: '1px solid #2a2a4a',
                  background: index % 2 === 0 ? '#1a1a2e' : '#16213e',
                  display: 'flex',
                  gap: '10px',
                  alignItems: 'flex-start'
                }}
              >
                {/* 快照缩略图 */}
                {screenshotUrl && (
                  <div
                    onClick={() => setSelectedViolation(v)}
                    style={{
                      width: '60px',
                      height: '45px',
                      borderRadius: '4px',
                      overflow: 'hidden',
                      border: '2px solid #f44336',
                      cursor: 'pointer',
                      flexShrink: 0,
                      position: 'relative'
                    }}
                    title="点击查看大图"
                  >
                    <img
                      src={screenshotUrl}
                      alt="违规快照"
                      style={{
                        width: '100%',
                        height: '100%',
                        objectFit: 'cover'
                      }}
                      onError={(e) => {
                        // 图片加载失败时隐藏
                        (e.target as HTMLImageElement).style.display = 'none';
                      }}
                    />
                    <div style={{
                      position: 'absolute',
                      bottom: '0',
                      right: '0',
                      background: 'rgba(0,0,0,0.7)',
                      padding: '1px 3px',
                      fontSize: '8px',
                      color: '#fff'
                    }}>
                      🔍
                    </div>
                  </div>
                )}
                
                {/* 违规信息 */}
                <div style={{ flex: 1, minWidth: 0 }}>
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
                  {screenshotUrl && (
                    <div 
                      onClick={() => setSelectedViolation(v)}
                      style={{ 
                        color: '#2196f3', 
                        fontSize: '10px', 
                        marginTop: '3px',
                        cursor: 'pointer'
                      }}
                    >
                      📷 点击查看快照
                    </div>
                  )}
                </div>
              </div>
            );
          })
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

      {/* 快照大图弹窗 */}
      {selectedViolation && (
        <div
          onClick={() => setSelectedViolation(null)}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0, 0, 0, 0.9)',
            zIndex: 10000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px',
            cursor: 'pointer'
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              background: '#1a1a2e',
              borderRadius: '10px',
              overflow: 'hidden',
              maxWidth: '90vw',
              maxHeight: '90vh',
              boxShadow: '0 10px 50px rgba(0, 0, 0, 0.5)'
            }}
          >
            {/* 弹窗标题 */}
            <div style={{
              padding: '15px 20px',
              background: '#16213e',
              borderBottom: '1px solid #333',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <div>
                <span style={{ color: '#ff9800', fontWeight: 'bold', fontSize: '16px' }}>
                  {selectedViolation.type === 'red_light_running' ? '🚦 闯红灯' :
                   selectedViolation.type === 'wrong_way_driving' ? '⬅️ 逆行' :
                   selectedViolation.type === 'lane_change_across_solid_line' ? '〰️ 压线变道' :
                   selectedViolation.type === 'waiting_area_red_entry' ? '🔴 待转区红灯进入' :
                   selectedViolation.type === 'waiting_area_illegal_exit' ? '⚠️ 待转区非法驶离' :
                   selectedViolation.type}
                </span>
                <span style={{ color: '#888', marginLeft: '15px', fontSize: '13px' }}>
                  方向: {selectedViolation.direction} | 车辆ID: {selectedViolation.track_id}
                </span>
                {selectedViolation.confidence !== undefined && (
                  <span style={{ color: '#4caf50', marginLeft: '15px', fontSize: '13px' }}>
                    置信度: {(selectedViolation.confidence * 100).toFixed(1)}%
                  </span>
                )}
              </div>
              <button
                onClick={() => setSelectedViolation(null)}
                style={{
                  background: '#f44336',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '50%',
                  width: '30px',
                  height: '30px',
                  cursor: 'pointer',
                  fontSize: '16px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center'
                }}
              >
                ✕
              </button>
            </div>
            
            {/* 快照图片 */}
            <div style={{ padding: '10px', background: '#0f0f23' }}>
              <img
                src={getScreenshotUrl(selectedViolation) || ''}
                alt="违规快照"
                style={{
                  maxWidth: '100%',
                  maxHeight: '70vh',
                  display: 'block',
                  margin: '0 auto',
                  borderRadius: '5px',
                  border: '3px solid #f44336'
                }}
              />
            </div>
            
            {/* 底部信息 */}
            <div style={{
              padding: '10px 20px',
              background: '#16213e',
              borderTop: '1px solid #333',
              color: '#888',
              fontSize: '12px',
              textAlign: 'center'
            }}>
              违规ID: {selectedViolation.id} | 时间: {selectedViolation.timestamp || '未知'}
              <br />
              <span style={{ fontSize: '11px', color: '#666' }}>
                点击弹窗外部或按 ✕ 关闭
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
});

// ==================== 图片检测面板组件 ====================

interface ImageDetectionPanelProps {
  apiBase: string;
}

const ImageDetectionPanel: React.FC<ImageDetectionPanelProps> = ({ apiBase }) => {
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string>('');
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [selectedViolation, setSelectedViolation] = useState<any>(null);

  // 处理图片选择
  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedFile(file);
      setPreview(URL.createObjectURL(file));
      setResult(null);
    }
  };

  // 执行检测
  const handleDetect = async () => {
    if (!selectedFile) return;

    setLoading(true);
    const formData = new FormData();
    formData.append('image', selectedFile);

    try {
      const response = await fetch(`${apiBase}/detect-image`, {
        method: 'POST',
        body: formData,
      });

      const data = await response.json();
      console.log('🔍 图片检测结果:', data);
      setResult(data);
    } catch (error) {
      console.error('检测失败:', error);
      alert('检测失败: ' + error);
    } finally {
      setLoading(false);
    }
  };

  // 清空结果
  const handleClear = () => {
    setSelectedFile(null);
    setPreview('');
    setResult(null);
  };

  return (
    <div style={{
      display: 'flex',
      gap: '20px',
      flexWrap: 'wrap'
    }}>
      {/* 左侧：图片上传和预览 */}
      <div style={{
        flex: '1',
        minWidth: '400px',
        background: '#1a1a2e',
        borderRadius: '10px',
        overflow: 'hidden',
        border: '2px solid #333'
      }}>
        <div style={{
          background: '#16213e',
          padding: '15px 20px',
          borderBottom: '1px solid #333'
        }}>
          <h3 style={{ margin: 0, color: '#fff', fontSize: '16px' }}>
            📸 图片上传
          </h3>
        </div>

        <div style={{ padding: '20px' }}>
          {/* 文件选择 */}
          <label style={{
            display: 'block',
            padding: '30px',
            background: '#12122a',
            border: '2px dashed #444',
            borderRadius: '10px',
            textAlign: 'center',
            cursor: 'pointer',
            marginBottom: '15px'
          }}>
            <input
              type="file"
              accept="image/*"
              onChange={handleFileChange}
              style={{ display: 'none' }}
            />
            <div style={{ fontSize: '40px', marginBottom: '10px' }}>📁</div>
            <div style={{ color: '#888' }}>
              {selectedFile ? selectedFile.name : '点击选择图片或拖放到这里'}
            </div>
          </label>

          {/* 图片预览 */}
          {preview && (
            <div style={{ marginBottom: '15px' }}>
              <img
                src={preview}
                alt="预览"
                style={{
                  width: '100%',
                  maxHeight: '300px',
                  objectFit: 'contain',
                  borderRadius: '5px',
                  border: '1px solid #333'
                }}
              />
            </div>
          )}

          {/* 操作按钮 */}
          <div style={{ display: 'flex', gap: '10px' }}>
            <button
              onClick={handleDetect}
              disabled={!selectedFile || loading}
              style={{
                flex: 1,
                padding: '12px 20px',
                background: loading ? '#666' : 'linear-gradient(135deg, #4caf50, #2196f3)',
                color: '#fff',
                border: 'none',
                borderRadius: '25px',
                cursor: loading || !selectedFile ? 'not-allowed' : 'pointer',
                fontSize: '14px',
                fontWeight: 'bold'
              }}
            >
              {loading ? '⏳ 检测中...' : '🔍 开始检测'}
            </button>
            <button
              onClick={handleClear}
              style={{
                padding: '12px 20px',
                background: '#666',
                color: '#fff',
                border: 'none',
                borderRadius: '25px',
                cursor: 'pointer',
                fontSize: '14px'
              }}
            >
              ✕ 清空
            </button>
          </div>
        </div>
      </div>

      {/* 右侧：检测结果 */}
      <div style={{
        flex: '1',
        minWidth: '400px',
        background: '#1a1a2e',
        borderRadius: '10px',
        overflow: 'hidden',
        border: '2px solid #333'
      }}>
        <div style={{
          background: '#16213e',
          padding: '15px 20px',
          borderBottom: '1px solid #333',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <h3 style={{ margin: 0, color: '#fff', fontSize: '16px' }}>
            检测结果
          </h3>
          {result && (
            <span style={{
              padding: '4px 12px',
              borderRadius: '15px',
              fontSize: '12px',
              background: result.total_violations > 0 ? '#f44336' : '#4caf50',
              color: '#fff'
            }}>
              {result.total_violations} 个违规
            </span>
          )}
        </div>

        <div style={{ padding: '20px', maxHeight: '500px', overflowY: 'auto' }}>
          {result ? (
            <>
              {/* 标注后的图片 */}
              {result.annotated_image && (
                <div style={{ marginBottom: '20px' }}>
                  <h4 style={{ color: '#fff', margin: '0 0 10px 0', fontSize: '14px' }}>
                    🖼️ 标注结果
                  </h4>
                  <img
                    src={`data:image/jpeg;base64,${result.annotated_image}`}
                    alt="检测结果"
                    style={{
                      width: '100%',
                      borderRadius: '5px',
                      border: '2px solid #4caf50'
                    }}
                  />
                </div>
              )}

              {/* 违规列表 */}
              {result.violations?.length > 0 ? (
                <div>
                  <h4 style={{ color: '#fff', margin: '0 0 10px 0', fontSize: '14px' }}>
                    🚨 违规详情
                  </h4>
                  {result.violations.map((v: any, index: number) => (
                    <div
                      key={index}
                      onClick={() => setSelectedViolation(v)}
                      style={{
                        padding: '12px 15px',
                        marginBottom: '10px',
                        background: '#16213e',
                        borderLeft: '4px solid #ff9800',
                        borderRadius: '5px',
                        cursor: 'pointer'
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ color: '#ff9800', fontWeight: 'bold', fontSize: '14px' }}>
                          {v.type === 'red_light_running' ? '🚦 闯红灯' :
                           v.type === 'wrong_way_driving' ? '⬅️ 逆行' :
                           v.type === 'lane_change_across_solid_line' ? '〰️ 压线变道' :
                           v.type === 'waiting_area_red_entry' ? '🔴 待转区红灯进入' :
                           v.type === 'waiting_area_illegal_exit' ? '⚠️ 待转区非法驶离' :
                           v.type}
                        </span>
                        <span style={{ color: '#888', fontSize: '12px' }}>
                          {v.direction}
                        </span>
                      </div>
                      {v.confidence !== undefined && (
                        <div style={{ color: '#4caf50', fontSize: '12px', marginTop: '5px' }}>
                          置信度: {(v.confidence * 100).toFixed(1)}%
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <div style={{
                  padding: '40px',
                  textAlign: 'center',
                  color: '#4caf50'
                }}>
                  <div style={{ fontSize: '40px', marginBottom: '10px' }}>✅</div>
                  <div>未检测到违规行为</div>
                </div>
              )}
            </>
          ) : (
            <div style={{
              padding: '60px 20px',
              textAlign: 'center',
              color: '#666'
            }}>
              <div style={{ fontSize: '50px', marginBottom: '15px' }}>🔍</div>
              <div>上传图片并点击"开始检测"</div>
              <div style={{ fontSize: '12px', marginTop: '10px' }}>
                支持 JPG、PNG 格式
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 违规详情弹窗 */}
      {selectedViolation && (
        <div
          onClick={() => setSelectedViolation(null)}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: 'rgba(0,0,0,0.9)',
            zIndex: 10000,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px'
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              background: '#1a1a2e',
              borderRadius: '10px',
              padding: '20px',
              maxWidth: '500px',
              width: '100%'
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
              <h3 style={{ margin: 0, color: '#ff9800' }}>
                {selectedViolation.type === 'red_light_running' ? '🚦 闯红灯' :
                 selectedViolation.type === 'wrong_way_driving' ? '⬅️ 逆行' :
                 selectedViolation.type === 'lane_change_across_solid_line' ? '〰️ 压线变道' :
                 selectedViolation.type}
              </h3>
              <button
                onClick={() => setSelectedViolation(null)}
                style={{
                  background: '#f44336',
                  color: '#fff',
                  border: 'none',
                  borderRadius: '50%',
                  width: '30px',
                  height: '30px',
                  cursor: 'pointer'
                }}
              >
                ✕
              </button>
            </div>
            <div style={{ color: '#888' }}>
              <p><strong>方向:</strong> {selectedViolation.direction}</p>
              {selectedViolation.confidence !== undefined && (
                <p><strong>置信度:</strong> {(selectedViolation.confidence * 100).toFixed(1)}%</p>
              )}
              <p><strong>违规ID:</strong> {selectedViolation.id || '未知'}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

// ==================== 双视频窗口主组件 ====================

export const DualVideoMonitor: React.FC = () => {
  const API_BASE = 'http://localhost:5000';

  // 模式切换：video（视频检测） 或 image（图片检测）
  const [mode, setMode] = useState<'video' | 'image'>('video');

  // 子组件引用
  const window1Ref = useRef<SingleVideoWindowRef>(null);
  const window2Ref = useRef<SingleVideoWindowRef>(null);

  // 共享状态
  const [videoFile, setVideoFile] = useState<File | null>(null);
  const [startingBoth, setStartingBoth] = useState(false);
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

  // 同时启动两个窗口
  const startBothWindows = async () => {
    if (!videoFile) {
      alert('请先选择视频文件');
      return;
    }

    setStartingBoth(true);

    try {
      // 同时启动两个窗口
      const promises: Promise<void>[] = [];
      
      if (window1Ref.current?.isIdle()) {
        promises.push(window1Ref.current.startDetection());
      }
      if (window2Ref.current?.isIdle()) {
        promises.push(window2Ref.current.startDetection());
      }

      await Promise.all(promises);
      console.log('两个窗口已同时启动');
    } catch (error) {
      console.error('启动失败:', error);
    } finally {
      setStartingBoth(false);
    }
  };

  // 同时重置两个窗口
  const resetBothWindows = () => {
    window1Ref.current?.resetDetection();
    window2Ref.current?.resetDetection();
    console.log('两个窗口已重置');
  };

  // 同时重新检测
  const restartBothWindows = async () => {
    if (!videoFile) {
      alert('请先选择视频文件');
      return;
    }

    // 先重置
    resetBothWindows();

    // 等待状态更新后重新启动
    setTimeout(async () => {
      setStartingBoth(true);
      try {
        const promises: Promise<void>[] = [];
        promises.push(window1Ref.current?.startDetection() || Promise.resolve());
        promises.push(window2Ref.current?.startDetection() || Promise.resolve());
        await Promise.all(promises);
        console.log('两个窗口已重新启动');
      } catch (error) {
        console.error('重新启动失败:', error);
      } finally {
        setStartingBoth(false);
      }
    }, 200);
  };

  // 连接全局 WebSocket 接收信号灯状态
  useEffect(() => {
    const socket = io(API_BASE, {
      transports: ['websocket']
    });

    socket.on('connect', () => {
      console.log('全局 WebSocket 已连接（信号灯同步）');
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
      {/* 标题和模式切换 */}
      <div style={{
        textAlign: 'center',
        marginBottom: '20px'
      }}>
        <h1 style={{
          color: '#fff',
          margin: '0 0 15px 0',
          fontSize: '24px'
        }}>
          🚦 TrafficMind 智能交通检测
        </h1>

        {/* 模式切换标签 */}
        <div style={{
          display: 'inline-flex',
          background: '#16213e',
          borderRadius: '30px',
          padding: '5px',
          marginBottom: '10px'
        }}>
          <button
            onClick={() => setMode('video')}
            style={{
              padding: '10px 25px',
              background: mode === 'video' ? 'linear-gradient(135deg, #4caf50, #2196f3)' : 'transparent',
              color: '#fff',
              border: 'none',
              borderRadius: '25px',
              cursor: 'pointer',
              fontSize: '14px',
              fontWeight: 'bold',
              transition: 'all 0.3s ease'
            }}
          >
            🎥 视频检测
          </button>
          <button
            onClick={() => setMode('image')}
            style={{
              padding: '10px 25px',
              background: mode === 'image' ? 'linear-gradient(135deg, #ff9800, #f44336)' : 'transparent',
              color: '#fff',
              border: 'none',
              borderRadius: '25px',
              cursor: 'pointer',
              fontSize: '14px',
              fontWeight: 'bold',
              transition: 'all 0.3s ease'
            }}
          >
            📸 图片检测
          </button>
        </div>

        <p style={{ color: '#888', margin: 0, fontSize: '14px' }}>
          {mode === 'video' 
            ? '双窗口视频流检测 · 实时违规监控' 
            : '单张图片检测 · 快速违规分析'}
        </p>
      </div>

      {/* 根据模式显示不同内容 */}
      {mode === 'image' ? (
        <ImageDetectionPanel apiBase={API_BASE} />
      ) : (
        <>
      {/* 视频选择 */}
      <div style={{
        background: '#16213e',
        borderRadius: '10px',
        padding: '20px',
        marginBottom: '20px',
        textAlign: 'center'
      }}>
        <h3 style={{ color: '#fff', margin: '0 0 10px 0', fontSize: '16px' }}>
          📹 共享视频（可选）
        </h3>
        <p style={{ color: '#888', margin: '0 0 15px 0', fontSize: '12px' }}>
          在此选择的视频会作为两个窗口的默认视频，也可以在各窗口单独选择不同视频
        </p>
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
            已选择: {videoFile.name} ({(videoFile.size / 1024 / 1024).toFixed(2)} MB)
          </div>
        )}

        {/* 操作按钮组 */}
        {videoFile && (
          <div style={{
            marginTop: '15px',
            display: 'flex',
            gap: '10px',
            justifyContent: 'center',
            flexWrap: 'wrap'
          }}>
            {/* 同时启动按钮 */}
            <button
              onClick={startBothWindows}
              disabled={startingBoth}
              style={{
                padding: '12px 25px',
                background: startingBoth ? '#666' : 'linear-gradient(135deg, #4caf50, #2196f3)',
                color: '#fff',
                border: 'none',
                borderRadius: '25px',
                cursor: startingBoth ? 'not-allowed' : 'pointer',
                fontSize: '14px',
                fontWeight: 'bold',
                boxShadow: '0 4px 15px rgba(76, 175, 80, 0.4)',
                transition: 'all 0.3s ease'
              }}
            >
              {startingBoth ? '⏳ 启动中...' : ' 同时启动'}
            </button>

            {/* 重新检测按钮 */}
            <button
              onClick={restartBothWindows}
              disabled={startingBoth}
              style={{
                padding: '12px 25px',
                background: startingBoth ? '#666' : 'linear-gradient(135deg, #ff9800, #f44336)',
                color: '#fff',
                border: 'none',
                borderRadius: '25px',
                cursor: startingBoth ? 'not-allowed' : 'pointer',
                fontSize: '14px',
                fontWeight: 'bold',
                boxShadow: '0 4px 15px rgba(255, 152, 0, 0.4)',
                transition: 'all 0.3s ease'
              }}
            >
              重新检测
            </button>

            {/* 清空结果按钮 */}
            <button
              onClick={resetBothWindows}
              style={{
                padding: '12px 25px',
                background: '#666',
                color: '#fff',
                border: 'none',
                borderRadius: '25px',
                cursor: 'pointer',
                fontSize: '14px',
                fontWeight: 'bold',
                transition: 'all 0.3s ease'
              }}
            >
              ✕ 全部清空
            </button>
          </div>
        )}

        <p style={{ color: '#888', margin: '15px 0 0 0', fontSize: '12px' }}>
           同时启动：启动两个窗口 | 重新检测：重置并重新开始 | ✕ 全部清空：清除所有结果
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
          ref={window1Ref}
          title=" 视角 1 - 南北向检测"
          roisConfig="rois.json"
          sharedVideoFile={videoFile}
          signalLights={signalLights}
          leftTurnLights={leftTurnLights}
          apiBase={API_BASE}
        />

        {/* 窗口 2: 使用 rois2.json - 旋转方向 */}
        <SingleVideoWindow
          ref={window2Ref}
          title=" 视角 2 - 东西向检测"
          roisConfig="rois2.json"
          sharedVideoFile={videoFile}
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
        </>
      )}
    </div>
  );
};

export default DualVideoMonitor;

