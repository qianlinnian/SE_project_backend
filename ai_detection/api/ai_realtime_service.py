"""
AI 实时检测服务 - TrafficMind 交通智脑
支持 WebSocket 实时推送处理帧

功能：
1. 接收视频处理任务（HTTP API）
2. 逐帧处理视频，实时推送到前端（WebSocket）
3. 保存处理后的结果视频
4. 上报违规记录到后端

启动方式:
    conda activate yolov8
    cd SE_project_backend/ai_detection
    pip install flask flask-socketio flask-cors eventlet requests
    python api/ai_realtime_service.py

服务地址: http://localhost:5000
WebSocket: ws://localhost:5000
"""

import os
import sys
import cv2
import time
import json
import base64
import threading
import requests
import traceback
import numpy as np
from pathlib import Path
from datetime import datetime
from flask import Flask, request, jsonify
from flask_cors import CORS
from flask_socketio import SocketIO, emit

# 添加父目录到 Python 路径，确保可以导入 core 模块
_CURRENT_FILE_DIR = os.path.dirname(os.path.abspath(__file__))
_AI_DETECTION_DIR = os.path.dirname(_CURRENT_FILE_DIR)
if _AI_DETECTION_DIR not in sys.path:
    sys.path.insert(0, _AI_DETECTION_DIR)


def convert_to_serializable(obj):
    """将 NumPy 类型转换为 Python 原生类型，以便 JSON 序列化"""
    if isinstance(obj, np.integer):
        return int(obj)
    elif isinstance(obj, np.floating):
        return float(obj)
    elif isinstance(obj, np.ndarray):
        return obj.tolist()
    elif isinstance(obj, dict):
        return {k: convert_to_serializable(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [convert_to_serializable(item) for item in obj]
    elif isinstance(obj, tuple):
        return tuple(convert_to_serializable(item) for item in obj)
    return obj


# 导入现有的检测模块
from core.violation_detector import ViolationDetector
from core.vehicle_tracker import VehicleTracker
from ai_detection.tools.signal_adapter import SignalAdapter

# ==================== 配置 ====================
BACKEND_BASE_URL = "http://localhost:8081/api"
MINIO_ENDPOINT = "http://localhost:9000"
ROIS_PATH = "./data/rois.json"
MODEL_PATH = "./yolov8s.pt"  # Small 模型，更准确（也可用 yolov8s.pt 更快）
TEMP_VIDEO_DIR = "./temp_videos"
OUTPUT_VIDEO_DIR = "./output/videos"
VIOLATIONS_DIR = "./output/screenshots"

# 实时推流配置
TARGET_FPS = 12  # 推送帧率（降低以减少带宽）
JPEG_QUALITY = 70  # JPEG 压缩质量 (0-100)

# ==================== 初始化 ====================
app = Flask(__name__)
app.config['SECRET_KEY'] = 'trafficmind-secret-key'
CORS(app, origins="*")
socketio = SocketIO(app, cors_allowed_origins="*", async_mode='threading')

# 确保目录存在
Path(TEMP_VIDEO_DIR).mkdir(parents=True, exist_ok=True)
Path(OUTPUT_VIDEO_DIR).mkdir(parents=True, exist_ok=True)
Path(VIOLATIONS_DIR).mkdir(parents=True, exist_ok=True)

# 任务状态存储
tasks = {}

# 全局信号灯状态（由外部系统更新）
current_signal_states = {
    'north_bound': 'red',
    'south_bound': 'red',
    'east_bound': 'red',
    'west_bound': 'red'
}
current_left_turn_signals = {
    'north_bound': 'red',
    'south_bound': 'red',
    'east_bound': 'red',
    'west_bound': 'red'
}
signal_lock = threading.Lock()  # 线程安全锁


# ==================== HTTP API ====================

@app.route('/health', methods=['GET'])
def health_check():
    """健康检查"""
    return jsonify({
        "status": "healthy",
        "service": "TrafficMind AI Realtime Service",
        "version": "2.0.0",
        "timestamp": datetime.now().isoformat(),
        "websocket": "available"
    })


@app.route('/api/traffic', methods=['POST'])
def receive_traffic_signal():
    """
    接收外部系统的信号灯数据
    
    支持两种格式:
    
    格式1 - JSON列表:
    [
        {"路口": 0, "信号": "ETWT", "排队车辆": 4},
        {"路口": 1, "信号": "NTST", "排队车辆": 0},
        ...
    ]
    
    格式2 - 文本格式:
    {
        "data": "路口0: 信号=ETWT, 排队车辆=4\n路口1: 信号=NTST, 排队车辆=0\n..."
    }
    
    信号代码说明:
    - ETWT = 东西直行绿灯
    - NTST = 南北直行绿灯
    - ELWL = 东西左转绿灯
    - NLSL = 南北左转绿灯
    """
    global current_signal_states, current_left_turn_signals
    
    try:
        data = request.json
        
        # 解析信号灯数据
        if isinstance(data, list):
            # 格式1: JSON 列表
            signal_states = SignalAdapter.convert_backend_to_system(data)
        elif isinstance(data, dict) and 'data' in data:
            # 格式2: 文本格式
            signal_states = SignalAdapter.convert_backend_string_format(data['data'])
        elif isinstance(data, dict):
            # 格式3: 直接传入路口数据列表
            junction_list = []
            for key, value in data.items():
                if key.startswith('路口') or key.startswith('junction'):
                    if isinstance(value, dict):
                        junction_list.append(value)
            if junction_list:
                signal_states = SignalAdapter.convert_backend_to_system(junction_list)
            else:
                # 尝试解析为文本
                text_data = str(data)
                signal_states = SignalAdapter.convert_backend_string_format(text_data)
        else:
            return jsonify({
                "success": False,
                "message": "不支持的数据格式"
            }), 400
        
        # 更新全局信号灯状态（线程安全）
        with signal_lock:
            current_signal_states.update(signal_states)
        
        # 打印信号灯状态变化
        print(f"\n[信号灯更新] {datetime.now().strftime('%H:%M:%S')}")
        for direction, state in signal_states.items():
            emoji = "🟢" if state == "green" else "🔴"
            print(f"  {emoji} {direction}: {state}")
        
        # 广播信号灯状态给所有 WebSocket 客户端
        socketio.emit('signal_update', convert_to_serializable({
            'timestamp': datetime.now().isoformat(),
            'signals': signal_states
        }))
        
        return jsonify({
            "success": True,
            "message": "信号灯状态已更新",
            "signals": signal_states
        })
        
    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False,
            "message": f"解析信号灯数据失败: {str(e)}"
        }), 500


@app.route('/api/traffic/status', methods=['GET'])
def get_traffic_signal_status():
    """获取当前信号灯状态"""
    with signal_lock:
        return jsonify({
            "success": True,
            "signals": current_signal_states.copy(),
            "leftTurnSignals": current_left_turn_signals.copy(),
            "timestamp": datetime.now().isoformat()
        })


@app.route('/start-realtime', methods=['POST'])
def start_realtime_processing():
    """
    启动实时视频处理任务
    
    请求体:
    {
        "taskId": "xxx",
        "videoUrl": "http://...",  // MinIO 视频地址
        "videoPath": "/local/path.mp4",  // 或本地路径（二选一）
        "intersectionId": 1,
        "direction": "SOUTH"
    }
    
    返回:
    {
        "success": true,
        "taskId": "xxx",
        "message": "任务已启动，请通过 WebSocket 接收实时帧"
    }
    """
    try:
        data = request.json
        task_id = data.get('taskId', f"task_{int(time.time())}")
        video_url = data.get('videoUrl')
        video_path = data.get('videoPath')
        intersection_id = data.get('intersectionId', 1)
        direction = data.get('direction', 'SOUTH')
        
        # 校验参数
        if not video_url and not video_path:
            return jsonify({
                "success": False,
                "message": "缺少 videoUrl 或 videoPath 参数"
            }), 400
        
        # 初始化任务状态
        tasks[task_id] = {
            "status": "starting",
            "progress": 0,
            "startTime": datetime.now().isoformat(),
            "violations": [],
            "error": None
        }
        
        # 异步启动处理
        thread = threading.Thread(
            target=process_video_realtime,
            args=(task_id, video_url, video_path, intersection_id, direction)
        )
        thread.daemon = True
        thread.start()
        
        return jsonify({
            "success": True,
            "taskId": task_id,
            "message": "任务已启动，请通过 WebSocket 连接并监听 'frame' 和 'violation' 事件"
        })
        
    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False,
            "message": f"启动失败: {str(e)}"
        }), 500


@app.route('/task/<task_id>', methods=['GET'])
def get_task_status(task_id):
    """获取任务状态"""
    if task_id in tasks:
        return jsonify({
            "success": True,
            "task": tasks[task_id]
        })
    return jsonify({
        "success": False,
        "message": "任务不存在"
    }), 404


@app.route('/test-local', methods=['POST'])
def test_with_local_video():
    """
    使用本地视频测试（便于开发调试）
    
    请求体:
    {
        "videoName": "car_1_cross.mp4"  // data 目录下的视频文件名
    }
    """
    try:
        data = request.json
        video_name = data.get('videoName', 'car_1_cross.mp4')
        video_path = os.path.join('./data', video_name)
        
        if not os.path.exists(video_path):
            return jsonify({
                "success": False,
                "message": f"视频不存在: {video_path}"
            }), 404
        
        task_id = f"test_{int(time.time())}"
        
        # 初始化任务
        tasks[task_id] = {
            "status": "starting",
            "progress": 0,
            "startTime": datetime.now().isoformat(),
            "violations": [],
            "error": None
        }
        
        # 异步启动
        thread = threading.Thread(
            target=process_video_realtime,
            args=(task_id, None, video_path, 1, 'SOUTH')
        )
        thread.daemon = True
        thread.start()
        
        return jsonify({
            "success": True,
            "taskId": task_id,
            "videoPath": video_path,
            "message": "本地视频测试任务已启动"
        })
        
    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False,
            "message": str(e)
        }), 500


# ==================== WebSocket 事件 ====================

@socketio.on('connect')
def handle_connect():
    """客户端连接"""
    print(f"[WebSocket] 客户端连接: {request.sid}")
    emit('connected', {'message': 'Connected to AI Realtime Service'})


@socketio.on('disconnect')
def handle_disconnect():
    """客户端断开"""
    print(f"[WebSocket] 客户端断开: {request.sid}")


@socketio.on('subscribe')
def handle_subscribe(data):
    """订阅任务的实时帧"""
    task_id = data.get('taskId')
    if task_id:
        print(f"[WebSocket] 客户端 {request.sid} 订阅任务: {task_id}")
        emit('subscribed', {'taskId': task_id, 'message': f'已订阅任务 {task_id}'})


# ==================== 核心处理逻辑 ====================

def process_video_realtime(task_id: str, video_url: str, video_path: str,
                           intersection_id: int, direction: str):
    """
    实时处理视频并推送帧
    
    Args:
        task_id: 任务ID
        video_url: 视频URL（从MinIO下载）
        video_path: 本地视频路径（直接使用）
        intersection_id: 路口ID
        direction: 方向
    """
    try:
        print(f"\n{'='*60}")
        print(f"[任务 {task_id}] 开始实时处理")
        print(f"{'='*60}")
        
        tasks[task_id]["status"] = "downloading"
        
        # 1. 获取视频路径
        if video_path and os.path.exists(video_path):
            local_video_path = video_path
            print(f"[任务 {task_id}] 使用本地视频: {local_video_path}")
        elif video_url:
            local_video_path = download_video(video_url, task_id)
            if not local_video_path:
                raise Exception("视频下载失败")
            print(f"[任务 {task_id}] 视频下载完成: {local_video_path}")
        else:
            raise Exception("无有效视频源")
        
        tasks[task_id]["status"] = "initializing"
        
        # 2. 初始化检测器（复用现有代码）
        print(f"[任务 {task_id}] 初始化检测器...")
        
        tracker = VehicleTracker(model_path=MODEL_PATH, conf_threshold=0.25)
        detector = ViolationDetector(
            rois_path=ROIS_PATH,
            screenshot_dir=VIOLATIONS_DIR,
            intersection_id=intersection_id,
            enable_api=True  # 启用API自动上报
        )
        
        # 从全局状态初始化信号灯（会在处理过程中实时更新）
        with signal_lock:
            for dir_key, state in current_signal_states.items():
                detector.traffic_lights[dir_key] = state
            for dir_key, state in current_left_turn_signals.items():
                detector.left_turn_signals[dir_key] = state
        print(f"[任务 {task_id}] 信号灯状态已从全局状态初始化")
        
        # 3. 打开视频
        cap = cv2.VideoCapture(local_video_path)
        if not cap.isOpened():
            raise Exception(f"无法打开视频: {local_video_path}")
        
        # 获取视频信息
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        video_fps = cap.get(cv2.CAP_PROP_FPS) or 30
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        
        print(f"[任务 {task_id}] 视频信息: {width}x{height}, {video_fps}FPS, {total_frames}帧")
        
        # 4. 创建输出视频
        output_video_path = os.path.join(OUTPUT_VIDEO_DIR, f"{task_id}_result.mp4")
        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        video_writer = cv2.VideoWriter(output_video_path, fourcc, video_fps, (width, height))
        
        tasks[task_id]["status"] = "processing"
        
        # 5. 计算帧间隔（控制推送帧率）
        frame_interval = max(1, int(video_fps / TARGET_FPS))
        
        # 6. 逐帧处理
        frame_count = 0
        processed_count = 0
        violations_detected = []
        start_time = time.time()
        
        print(f"[任务 {task_id}] 开始逐帧处理...")
        
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break
            
            frame_count += 1
            timestamp_ms = frame_count / video_fps * 1000
            
            # 更新进度
            progress = int((frame_count / total_frames) * 100)
            tasks[task_id]["progress"] = progress
            
            # 实时更新信号灯状态（从全局状态）
            with signal_lock:
                for dir_key, state in current_signal_states.items():
                    detector.traffic_lights[dir_key] = state
                for dir_key, state in current_left_turn_signals.items():
                    detector.left_turn_signals[dir_key] = state
            
            # 车辆检测与追踪
            tracks = tracker.detect_and_track(frame)
            
            # 违规检测
            new_violations = detector.process_frame(frame, tracks, timestamp_ms)
            
            # 绘制检测结果
            annotated_frame = draw_detection_results(frame, tracks, new_violations, detector, tracker)
            
            # 写入输出视频
            video_writer.write(annotated_frame)
            
            # 记录违规
            if new_violations:
                for v in new_violations:
                    v['frameNumber'] = frame_count
                    v['timestamp'] = datetime.now().isoformat()
                    violations_detected.append(v)
                    
                    # 推送违规事件（转换 NumPy 类型）
                    socketio.emit('violation', convert_to_serializable({
                        'taskId': task_id,
                        'violation': v,
                        'frameNumber': frame_count
                    }))
            
            # 按帧间隔推送（控制帧率）
            if frame_count % frame_interval == 0:
                # 编码帧为 JPEG
                encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), JPEG_QUALITY]
                _, buffer = cv2.imencode('.jpg', annotated_frame, encode_param)
                frame_base64 = base64.b64encode(buffer).decode('utf-8')
                
                # 推送帧到所有连接的客户端
                socketio.emit('frame', convert_to_serializable({
                    'taskId': task_id,
                    'frameNumber': frame_count,
                    'progress': progress,
                    'image': frame_base64,
                    'violations': len(violations_detected)
                }))
                
                processed_count += 1
            
            # 控制处理速度（模拟实时）
            # time.sleep(1 / TARGET_FPS)  # 取消注释可模拟实时速度
        
        # 7. 清理
        cap.release()
        video_writer.release()
        
        # 8. 计算统计
        elapsed_time = time.time() - start_time
        actual_fps = frame_count / elapsed_time if elapsed_time > 0 else 0
        
        # 9. 获取违规统计
        violation_summary = detector.get_violation_summary()
        
        # 10. 更新任务状态
        tasks[task_id]["status"] = "completed"
        tasks[task_id]["progress"] = 100
        tasks[task_id]["violations"] = violations_detected
        tasks[task_id]["result"] = {
            "totalFrames": frame_count,
            "processedFrames": processed_count,
            "elapsedTime": round(elapsed_time, 2),
            "actualFps": round(actual_fps, 2),
            "outputVideoPath": output_video_path,
            "violationSummary": violation_summary
        }
        
        # 11. 推送完成事件
        socketio.emit('complete', convert_to_serializable({
            'taskId': task_id,
            'result': tasks[task_id]["result"],
            'message': '处理完成'
        }))
        
        print(f"\n{'='*60}")
        print(f"[任务 {task_id}] 处理完成!")
        print(f"  总帧数: {frame_count}")
        print(f"  推送帧数: {processed_count}")
        print(f"  处理时间: {elapsed_time:.2f}秒")
        print(f"  实际FPS: {actual_fps:.2f}")
        print(f"  检测到违规: {len(violations_detected)}条")
        print(f"  输出视频: {output_video_path}")
        print(f"{'='*60}\n")
        
        # 12. 导出违规记录
        if violations_detected:
            violations_json_path = os.path.join(VIOLATIONS_DIR, f"{task_id}_violations.json")
            detector.export_violations(violations_json_path)
        
    except Exception as e:
        traceback.print_exc()
        tasks[task_id]["status"] = "failed"
        tasks[task_id]["error"] = str(e)
        
        socketio.emit('error', {
            'taskId': task_id,
            'message': f'处理失败: {str(e)}'
        })


def download_video(video_url: str, task_id: str) -> str:
    """从 URL 下载视频"""
    try:
        print(f"[下载] 开始下载: {video_url}")
        
        response = requests.get(video_url, stream=True, timeout=60)
        response.raise_for_status()
        
        # 保存到临时目录
        file_ext = video_url.split('.')[-1].split('?')[0] or 'mp4'
        local_path = os.path.join(TEMP_VIDEO_DIR, f"{task_id}.{file_ext}")
        
        with open(local_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=8192):
                f.write(chunk)
        
        print(f"[下载] 完成: {local_path}")
        return local_path
        
    except Exception as e:
        print(f"[下载] 失败: {e}")
        return None


def draw_detection_results(frame, tracks, violations, detector, tracker=None):
    """
    在帧上绘制检测结果
    
    Args:
        frame: 原始帧
        tracks: 车辆追踪结果 [(track_id, (x1, y1, x2, y2)), ...]
        violations: 当前帧的违规列表
        detector: 违规检测器（用于获取统计信息）
        tracker: 车辆追踪器（用于绘制检测框）
    
    Returns:
        标注后的帧
    """
    annotated = frame.copy()
    
    # 1. 使用 tracker 的绘制方法（如果可用）
    if tracker is not None:
        annotated = tracker.draw_detections(annotated, tracks)
    else:
        # 手动绘制车辆检测框
        # tracks 格式: [(track_id, (x1, y1, x2, y2)), ...]
        for track in tracks:
            if isinstance(track, tuple) and len(track) == 2:
                track_id, bbox = track
                if len(bbox) == 4:
                    x1, y1, x2, y2 = map(int, bbox)
                    # 绘制边框
                    cv2.rectangle(annotated, (x1, y1), (x2, y2), (0, 255, 0), 2)
                    # 绘制标签
                    label = f"ID:{track_id}"
                    cv2.putText(annotated, label, (x1, y1 - 10),
                               cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 255, 0), 2)
    
    # 2. 绘制违规警告
    if violations:
        # 红色警告框
        cv2.rectangle(annotated, (10, 10), (400, 80), (0, 0, 255), -1)
        cv2.putText(annotated, f"VIOLATION DETECTED!", (20, 40),
                   cv2.FONT_HERSHEY_SIMPLEX, 0.8, (255, 255, 255), 2)
        cv2.putText(annotated, f"Count: {len(violations)}", (20, 65),
                   cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)
    
    # 3. 绘制统计信息
    summary = detector.get_violation_summary()
    stats_text = f"Total: {summary['total_violations']} | Red: {summary['red_light_running']} | Wrong: {summary['wrong_way_driving']}"
    
    # 底部信息栏
    h = annotated.shape[0]
    cv2.rectangle(annotated, (0, h - 40), (annotated.shape[1], h), (0, 0, 0), -1)
    cv2.putText(annotated, stats_text, (10, h - 15),
               cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
    
    return annotated


# ==================== 图片检测模块 ====================

# 延迟导入图片检测模块（避免启动时加载）
_image_detector = None

def get_image_detector():
    """获取图片检测器（懒加载）"""
    global _image_detector
    if _image_detector is None:
        try:
            from core.image_violation_detector import ImageViolationDetector
            _image_detector = ImageViolationDetector(
                rois_path=ROIS_PATH,
                model_path=MODEL_PATH,
                screenshot_dir=VIOLATIONS_DIR,
                intersection_id=1,
                enable_api=True
            )
            print("[图片检测] 图片检测器初始化成功")
        except Exception as e:
            print(f"[图片检测] 初始化失败: {e}")
            return None
    return _image_detector


@app.route('/detect-image', methods=['POST'])
def detect_image():
    """
    检测单张图片的交通违规（闯红灯+压实线变道）

    请求方式: multipart/form-data
    参数:
        - image: 图片文件 (必填)
        - signals: 信号灯状态JSON (可选)
                  格式: {"north_bound": "red", "south_bound": "green", ...}
        - detect_types: 检测类型 (可选，默认检测两种)
                       格式: "red_light" 或 "lane_change" 或 "red_light,lane_change"

    返回:
        {
            "success": true,
            "image_name": "xxx.jpg",
            "image_size": [width, height],
            "total_violations": 2,
            "violations": [...],
            "summary": {
                "red_light": 1,
                "lane_change": 1
            }
        }
    """
    try:
        # 1. 验证图片文件
        if 'image' not in request.files:
            return jsonify({
                "success": False,
                "message": "缺少图片文件 (image)"
            }), 400

        image_file = request.files['image']
        if image_file.filename == '':
            return jsonify({
                "success": False,
                "message": "未选择图片文件"
            }), 400

        # 2. 解析信号灯状态
        signal_states = None
        signals_param = request.form.get('signals')
        if signals_param:
            try:
                signal_states = json.loads(signals_param)
            except json.JSONDecodeError:
                return jsonify({
                    "success": False,
                    "message": "信号灯状态JSON格式错误"
                }), 400

        # 3. 解析检测类型
        detect_types = ['red_light', 'lane_change']
        detect_param = request.form.get('detect_types')
        if detect_param:
            detect_types = [t.strip() for t in detect_param.split(',')]
            # 验证检测类型
            valid_types = {'red_light', 'lane_change'}
            for t in detect_types:
                if t not in valid_types:
                    return jsonify({
                        "success": False,
                        "message": f"不支持的检测类型: {t}，可选值: red_light, lane_change"
                    }), 400

        # 4. 读取图片
        import numpy as np
        import io
        image_bytes = image_file.read()
        nparr = np.frombuffer(image_bytes, np.uint8)
        image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if image is None:
            return jsonify({
                "success": False,
                "message": "无法解析图片文件"
            }), 400

        # 5. 执行检测
        detector = get_image_detector()
        if detector is None:
            return jsonify({
                "success": False,
                "message": "图片检测器初始化失败"
            }), 500

        result = detector.process_image(
            image_path=image_file.filename,
            signal_states=signal_states,
            detect_types=detect_types
        )

        if result is None:
            return jsonify({
                "success": False,
                "message": "图片处理失败"
            }), 500

        # 6. 返回结果
        return jsonify({
            "success": True,
            "image_name": image_file.filename,
            "image_size": [image.shape[1], image.shape[0]],
            "total_violations": result['total_violations'],
            "violations": convert_to_serializable(result['violations']),
            "summary": {
                "red_light": result['red_light_violations'],
                "lane_change": result['lane_change_violations']
            },
            "timestamp": datetime.now().isoformat()
        })

    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False,
            "message": f"检测失败: {str(e)}"
        }), 500


@app.route('/detect-image-base64', methods=['POST'])
def detect_image_base64():
    """
    检测Base64编码图片的交通违规

    请求体 (JSON):
    {
        "image": "base64编码的图片数据",
        "signals": {"north_bound": "red", ...},  // 可选
        "detect_types": "red_light,lane_change"   // 可选
    }

    返回: 同 /detect-image
    """
    try:
        data = request.json

        # 1. 验证图片数据
        if 'image' not in data:
            return jsonify({
                "success": False,
                "message": "缺少图片数据 (image)"
            }), 400

        # 2. 解析图片
        import base64
        image_data = data['image']
        if ',' in image_data:
            # 处理 data:image/jpeg;base64, 前缀
            image_data = image_data.split(',')[1]

        image_bytes = base64.b64decode(image_data)
        nparr = np.frombuffer(image_bytes, np.uint8)
        image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if image is None:
            return jsonify({
                "success": False,
                "message": "无法解析图片数据"
            }), 400

        # 3. 解析信号灯状态
        signal_states = data.get('signals')

        # 4. 解析检测类型
        detect_types = ['red_light', 'lane_change']
        detect_param = data.get('detect_types')
        if detect_param:
            detect_types = [t.strip() for t in detect_param.split(',')]

        # 5. 执行检测
        detector = get_image_detector()
        if detector is None:
            return jsonify({
                "success": False,
                "message": "图片检测器初始化失败"
            }), 500

        result = detector.process_image(
            image_path="uploaded_image.jpg",
            signal_states=signal_states,
            detect_types=detect_types
        )

        if result is None:
            return jsonify({
                "success": False,
                "message": "图片处理失败"
            }), 500

        # 6. 返回结果
        return jsonify({
            "success": True,
            "image_size": [image.shape[1], image.shape[0]],
            "total_violations": result['total_violations'],
            "violations": convert_to_serializable(result['violations']),
            "summary": {
                "red_light": result['red_light_violations'],
                "lane_change": result['lane_change_violations']
            },
            "timestamp": datetime.now().isoformat()
        })

    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False,
            "message": f"检测失败: {str(e)}"
        }), 500


@app.route('/detect-batch', methods=['POST'])
def detect_batch():
    """
    批量检测多张图片

    请求方式: multipart/form-data
    参数:
        - images: 多张图片文件 (必填)
        - signals: 信号灯状态JSON (可选)
        - detect_types: 检测类型 (可选)

    返回:
        {
            "success": true,
            "total_images": 10,
            "processed_images": 10,
            "total_violations": 5,
            "results": [
                {
                    "image_name": "img1.jpg",
                    "violations": [...]
                },
                ...
            ]
        }
    """
    try:
        # 1. 验证图片文件
        if 'images' not in request.files:
            return jsonify({
                "success": False,
                "message": "缺少图片文件 (images)"
            }), 400

        image_files = request.files.getlist('images')
        if not image_files or all(f.filename == '' for f in image_files):
            return jsonify({
                "success": False,
                "message": "未选择任何图片文件"
            }), 400

        # 2. 解析信号灯状态
        signal_states = None
        signals_param = request.form.get('signals')
        if signals_param:
            signal_states = json.loads(signals_param)

        # 3. 解析检测类型
        detect_types = ['red_light', 'lane_change']
        detect_param = request.form.get('detect_types')
        if detect_param:
            detect_types = [t.strip() for t in detect_param.split(',')]

        # 4. 初始化检测器
        detector = get_image_detector()
        if detector is None:
            return jsonify({
                "success": False,
                "message": "图片检测器初始化失败"
            }), 500

        # 5. 批量处理
        import io
        total_violations = 0
        results = []

        for image_file in image_files:
            if image_file.filename == '':
                continue

            try:
                image_bytes = image_file.read()
                nparr = np.frombuffer(image_bytes, np.uint8)
                image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

                if image is None:
                    results.append({
                        "image_name": image_file.filename,
                        "success": False,
                        "message": "无法解析图片"
                    })
                    continue

                result = detector.process_image(
                    image_path=image_file.filename,
                    signal_states=signal_states,
                    detect_types=detect_types
                )

                if result:
                    total_violations += result['total_violations']
                    results.append({
                        "image_name": image_file.filename,
                        "success": True,
                        "total_violations": result['total_violations'],
                        "red_light": result['red_light_violations'],
                        "lane_change": result['lane_change_violations']
                    })
                else:
                    results.append({
                        "image_name": image_file.filename,
                        "success": True,
                        "total_violations": 0
                    })

            except Exception as img_error:
                results.append({
                    "image_name": image_file.filename,
                    "success": False,
                    "message": str(img_error)
                })

        return jsonify({
            "success": True,
            "total_images": len(image_files),
            "processed_images": len([r for r in results if r.get('success')]),
            "total_violations": total_violations,
            "results": results,
            "timestamp": datetime.now().isoformat()
        })

    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False,
            "message": f"批量检测失败: {str(e)}"
        }), 500


# ==================== 启动服务 ====================

if __name__ == '__main__':
    print("\n" + "=" * 60)
    print("🚀 TrafficMind AI 实时检测服务")
    print("=" * 60)
    print(f"📍 HTTP API:    http://localhost:5000")
    print(f"📍 WebSocket:   ws://localhost:5000")
    print("=" * 60)
    print("📡 API 端点 - 视频流检测:")
    print(f"   POST /start-realtime   - 启动实时处理任务")
    print(f"   POST /test-local       - 本地视频测试")
    print(f"   POST /api/traffic      - 接收信号灯数据 ⭐")
    print(f"   GET  /api/traffic/status - 获取当前信号灯状态")
    print("=" * 60)
    print("📡 API 端点 - 图片检测 (新增):")
    print(f"   POST /detect-image         - 检测单张图片文件 ⭐")
    print(f"   POST /detect-image-base64  - 检测Base64图片")
    print(f"   POST /detect-batch         - 批量检测多张图片")
    print("=" * 60)
    
    # 检查必要文件
    if not os.path.exists(ROIS_PATH):
        print(f"⚠️  ROI 配置文件不存在: {ROIS_PATH}")
    if not os.path.exists(MODEL_PATH):
        print(f"⚠️  模型文件不存在: {MODEL_PATH}")
    
    print("\n📡 WebSocket 事件:")
    print("   - 'frame'     : 接收实时处理帧 (Base64 JPEG)")
    print("   - 'violation' : 接收违规检测事件")
    print("   - 'complete'  : 处理完成通知")
    print("   - 'error'     : 错误通知")
    print("\n" + "=" * 60 + "\n")
    
    # 启动服务
    socketio.run(app, host='0.0.0.0', port=5000, debug=False, allow_unsafe_werkzeug=True)

