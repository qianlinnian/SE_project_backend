/**
 * AI 交通检测 React 组件示例
 *
 * 这个示例展示了如何与 AI 检测后端进行交互
 * 后端 API 地址: http://localhost:5000
 */

import React, { useState, useEffect, useCallback } from 'react';
import { io, Socket } from 'socket.io-client';

// ==================== 类型定义 ====================

// 违规类型
type ViolationType =
  | 'red_light'
  | 'red_light_running'
  | 'wrong_way'
  | 'wrong_way_driving'
  | 'lane_change'
  | 'lane_change_across_solid_line'
  | 'waiting_area_red_entry'
  | 'waiting_area_illegal_exit';

// 车辆检测结果
interface VehicleDetection {
  track_id: number;
  bbox: [number, number, number, number]; // [x1, y1, x2, y2]
  class: string; // 车辆类型: 'car', 'truck', 'bus', 'motorcycle'
  confidence: number;
  direction: string;
}

// 违规记录
interface Violation {
  id: string;
  type: ViolationType;
  track_id: number;
  direction: string;
  confidence: number;
  timestamp: string;
  bbox: [number, number, number, number];
  screenshot?: string;
}

// 实时帧数据
interface FrameData {
  taskId: string;
  frameNumber: number;
  progress: number;
  image: string; // base64 编码的图片
  violations: number;
  detections: VehicleDetection[];
}

// 信号灯状态
interface SignalStatus {
  north: 'red' | 'green' | 'yellow';
  south: 'red' | 'green' | 'yellow';
  east: 'red' | 'green' | 'yellow';
  west: 'red' | 'green' | 'yellow';
}

// API 响应类型
interface DetectImageResponse {
  success: boolean;
  image_name: string;
  image_size: [number, number];
  total_violations: number;
  violations: Violation[];
  annotated_image: string; // base64
  timestamp: string;
}

// ==================== 组件 1: 图片检测演示 ====================

interface ImageDetectorProps {
  apiBaseUrl?: string;
}

export const ImageDetector: React.FC<ImageDetectorProps> = ({
  apiBaseUrl = 'http://localhost:5000'
}) => {
  const [selectedImage, setSelectedImage] = useState<File | null>(null);
  const [preview, setPreview] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<DetectImageResponse | null>(null);
  const [error, setError] = useState<string>('');

  // 处理图片选择
  const handleImageChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSelectedImage(file);
      setPreview(URL.createObjectURL(file));
      setResult(null);
      setError('');
    }
  };

  // 上传并检测图片
  const handleDetect = async () => {
    if (!selectedImage) return;

    setLoading(true);
    setError('');

    const formData = new FormData();
    formData.append('image', selectedImage);

    try {
      const response = await fetch(`${apiBaseUrl}/detect-image`, {
        method: 'POST',
        body: formData,
      });

      const data: DetectImageResponse = await response.json();

      if (data.success) {
        setResult(data);
      } else {
        setError('检测失败');
      }
    } catch (err) {
      setError('网络请求失败: ' + err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="detector-container">
      <h2>图片违规检测</h2>

      {/* 图片上传 */}
      <div className="upload-section">
        <input type="file" accept="image/*" onChange={handleImageChange} />
        {preview && <img src={preview} alt="预览" className="preview-image" />}
      </div>

      {/* 检测按钮 */}
      <button
        onClick={handleDetect}
        disabled={!selectedImage || loading}
        className="detect-btn"
      >
        {loading ? '检测中...' : '开始检测'}
      </button>

      {/* 错误信息 */}
      {error && <div className="error-message">{error}</div>}

      {/* 检测结果 */}
      {result && (
        <div className="result-section">
          <h3>检测结果</h3>
          <p>检测到 {result.total_violations} 个违规</p>

          {/* 标注后的图片 */}
          {result.annotated_image && (
            <img
              src={`data:image/jpeg;base64,${result.annotated_image}`}
              alt="标注结果"
              className="result-image"
            />
          )}

          {/* 违规列表 */}
          <div className="violation-list">
            {result.violations.map((v) => (
              <div key={v.id} className="violation-item">
                <span className={`violation-type ${v.type}`}>
                  {v.type === 'red_light' || v.type === 'red_light_running' ? '🚦 闯红灯' :
                   v.type === 'wrong_way' || v.type === 'wrong_way_driving' ? '⬅️ 逆行' :
                   v.type === 'lane_change' || v.type === 'lane_change_across_solid_line' ? '〰️ 压线变道' :
                   v.type === 'waiting_area_red_entry' ? '🔴 待转区红灯进入' :
                   v.type === 'waiting_area_illegal_exit' ? '⚠️ 待转区非法驶离' :
                   v.type}
                </span>
                {v.confidence !== undefined && (
                  <span>置信度: {(v.confidence * 100).toFixed(1)}%</span>
                )}
                <span>方向: {v.direction}</span>
                {v.screenshotUrl && (
                  <div style={{ marginTop: '10px' }}>
                    <img
                      src={v.screenshotUrl}
                      alt="违规快照"
                      style={{
                        maxWidth: '200px',
                        border: '2px solid #f44336',
                        borderRadius: '3px'
                      }}
                    />
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

// ==================== 组件 2: 实时视频监控 ====================

interface RealtimeMonitorProps {
  apiBaseUrl?: string;
  taskId: string;
}

export const RealtimeMonitor: React.FC<RealtimeMonitorProps> = ({
  apiBaseUrl = 'http://localhost:5000',
  taskId
}) => {
  const [connected, setConnected] = useState(false);
  const [frameData, setFrameData] = useState<FrameData | null>(null);
  const [violations, setViolations] = useState<Violation[]>([]);
  const [signalStatus, setSignalStatus] = useState<SignalStatus | null>(null);
  const [socket, setSocket] = useState<Socket | null>(null);

  // 连接 WebSocket
  useEffect(() => {
    const newSocket = io(apiBaseUrl, {
      transports: ['websocket'],
    });

    newSocket.on('connect', () => {
      console.log('WebSocket 已连接');
      setConnected(true);
      // 订阅任务
      newSocket.emit('subscribe', { taskId });
    });

    newSocket.on('disconnect', () => {
      console.log('WebSocket 已断开');
      setConnected(false);
    });

    // 接收实时帧数据
    newSocket.on('frame', (data: FrameData) => {
      setFrameData(data);
    });

    // 接收违规事件
    newSocket.on('violation', (data: { violation: Violation }) => {
      setViolations(prev => [data.violation, ...prev].slice(0, 50)); // 只保留最近50条
    });

    // 接收信号灯状态
    newSocket.on('traffic', (data: any) => {
      console.log('🚦 收到信号灯状态:', data);
      // 转换数据格式: north_bound -> north
      if (data && typeof data === 'object') {
        const converted: SignalStatus = {
          north: data.north_bound || 'red',
          south: data.south_bound || 'red',
          east: data.east_bound || 'red',
          west: data.west_bound || 'red',
        };
        setSignalStatus(converted);
      }
    });

    newSocket.on('complete', () => {
      console.log('处理完成');
      setConnected(false);
    });

    newSocket.on('error', (err) => {
      console.error('错误:', err);
    });

    setSocket(newSocket);

    return () => {
      newSocket.disconnect();
    };
  }, [apiBaseUrl, taskId]);

  return (
    <div className="monitor-container">
      <h2>实时交通监控</h2>

      {/* 连接状态 */}
      <div className="status-bar">
        <span className={`status-indicator ${connected ? 'connected' : 'disconnected'}`}>
          {connected ? '已连接' : '未连接'}
        </span>
        {frameData && (
          <span>进度: {frameData.progress}%</span>
        )}
      </div>

      {/* 信号灯状态 */}
      {signalStatus && (
        <div className="signal-status">
          <h4>🚦 信号灯状态</h4>
          <div style={{
            display: 'flex',
            gap: '20px',
            background: '#1a1a2e',
            padding: '15px 20px',
            borderRadius: '10px',
            marginBottom: '15px'
          }}>
            {Object.entries(signalStatus).map(([dir, status]) => (
              <div key={dir} style={{ textAlign: 'center' }}>
                <div style={{ color: '#888', fontSize: '12px', marginBottom: '5px', textTransform: 'uppercase' }}>
                  {dir}
                </div>
                <div style={{
                  width: '30px',
                  height: '30px',
                  borderRadius: '50%',
                  margin: '3px auto',
                  border: '2px solid #444',
                  background: status === 'green' ? '#00ff00' :
                             status === 'red' ? '#ff0000' :
                             status === 'yellow' ? '#ffff00' : '#440000',
                  boxShadow: status !== 'red' ? `0 0 20px ${status === 'green' ? '#00ff00' : '#ffff00'}` : 'none',
                }} />
              </div>
            ))}
          </div>
        </div>
      )}

      {/* 实时视频帧 */}
      <div className="video-frame">
        {frameData?.image ? (
          <img
            src={`data:image/jpeg;base64,${frameData.image}`}
            alt="实时画面"
          />
        ) : (
          <div className="placeholder">等待视频流...</div>
        )}
      </div>

      {/* 违规记录 */}
      <div className="violations-panel">
        <h4>实时违规记录 ({violations.length})</h4>
        <ul className="violation-feed">
          {violations.map((v) => (
            <li key={v.id} className="violation-feed-item">
              <span className={`badge ${v.type}`}>{v.type}</span>
              <span>ID: {v.track_id}</span>
              {v.confidence !== undefined && (
                <span>{(v.confidence * 100).toFixed(0)}%</span>
              )}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
};

// ==================== 组件 3: 启动实时任务 ====================

interface TaskStarterProps {
  apiBaseUrl?: string;
  onTaskCreated: (taskId: string) => void;
}

export const TaskStarter: React.FC<TaskStarterProps> = ({
  apiBaseUrl = 'http://localhost:5000',
  onTaskCreated
}) => {
  const [videoUrl, setVideoUrl] = useState('');
  const [loading, setLoading] = useState(false);
  const [taskId, setTaskId] = useState('');

  const handleStartTask = async () => {
    if (!videoUrl) return;

    setLoading(true);
    try {
      const response = await fetch(`${apiBaseUrl}/start-realtime`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          taskId: `task_${Date.now()}`,
          videoUrl,
          intersectionId: 1,
          direction: 'SOUTH',
        }),
      });

      const data = await response.json();
      const newTaskId = data.taskId || `task_${Date.now()}`;
      setTaskId(newTaskId);
      onTaskCreated(newTaskId);
    } catch (err) {
      console.error('启动任务失败:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="task-starter">
      <h3>启动实时检测任务</h3>
      <input
        type="text"
        placeholder="视频 URL"
        value={videoUrl}
        onChange={(e) => setVideoUrl(e.target.value)}
      />
      <button onClick={handleStartTask} disabled={loading || !videoUrl}>
        {loading ? '启动中...' : '启动任务'}
      </button>
      {taskId && <p>任务ID: {taskId}</p>}
    </div>
  );
};

// ==================== 组件 4: 违规列表展示 ====================

interface ViolationListProps {
  violations: Violation[];
  onViewDetail: (violation: Violation) => void;
}

export const ViolationList: React.FC<ViolationListProps> = ({
  violations,
  onViewDetail
}) => {
  // 按时间倒序
  const sortedViolations = [...violations].sort(
    (a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
  );

  return (
    <div className="violation-list-container">
      <h3>违规记录列表</h3>
      <table className="violation-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>类型</th>
            <th>方向</th>
            <th>车辆ID</th>
            <th>置信度</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          {sortedViolations.map((v) => (
            <tr key={v.id}>
              <td>{new Date(v.timestamp).toLocaleString()}</td>
              <td>
                <span className={`badge ${v.type}`}>
                  {v.type === 'red_light' || v.type === 'red_light_running' ? '🚦 闯红灯' :
                   v.type === 'wrong_way' || v.type === 'wrong_way_driving' ? '⬅️ 逆行' :
                   v.type === 'lane_change' || v.type === 'lane_change_across_solid_line' ? '〰️ 压线变道' :
                   v.type === 'waiting_area_red_entry' ? '🔴 待转区红灯进入' :
                   v.type === 'waiting_area_illegal_exit' ? '⚠️ 待转区非法驶离' :
                   v.type}
                </span>
              </td>
              <td>{v.direction}</td>
              <td>{v.track_id}</td>
              <td>{(v.confidence * 100).toFixed(1)}%</td>
              <td>
                <button onClick={() => onViewDetail(v)}>查看详情</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

// ==================== 组件 5: 完整的主组件 ====================

export const TrafficDetectionDemo: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'image' | 'realtime'>('image');
  const [currentTaskId, setCurrentTaskId] = useState<string>('');

  return (
    <div className="traffic-detection-demo">
      <h1>AI 交通检测演示</h1>

      {/* 标签切换 */}
      <div className="tab-bar">
        <button
          className={activeTab === 'image' ? 'active' : ''}
          onClick={() => setActiveTab('image')}
        >
          图片检测
        </button>
        <button
          className={activeTab === 'realtime' ? 'active' : ''}
          onClick={() => setActiveTab('realtime')}
        >
          实时监控
        </button>
      </div>

      {/* 内容区域 */}
      <div className="tab-content">
        {activeTab === 'image' ? (
          <ImageDetector />
        ) : (
          <>
            <TaskStarter onTaskCreated={setCurrentTaskId} />
            {currentTaskId && (
              <RealtimeMonitor taskId={currentTaskId} />
            )}
          </>
        )}
      </div>
    </div>
  );
};

export default TrafficDetectionDemo;
