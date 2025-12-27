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
    pip install flask flask-socketio flask-cors requests
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
from flask import Flask, request, jsonify, send_from_directory
from flask_cors import CORS
from flask_socketio import SocketIO, emit

# 添加父目录到 Python 路径，确保可以导入 core 模块
_CURRENT_FILE_DIR = os.path.dirname(os.path.abspath(__file__))
_AI_DETECTION_DIR = os.path.dirname(_CURRENT_FILE_DIR)
_AI_DETECTION_PATH = Path(_AI_DETECTION_DIR)
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
from tools.signal_adapter import SignalAdapter

# ==================== 配置 ====================
BACKEND_BASE_URL = "http://localhost:8081/api"
MINIO_ENDPOINT = "http://localhost:9000"
ROIS_PATH = str(_AI_DETECTION_PATH / "data" / "rois.json")
MODEL_PATH = str(_AI_DETECTION_PATH / "yolov8s.pt")  # Small 模型，更准确
TEMP_VIDEO_DIR = str(_AI_DETECTION_PATH / "temp_videos")
OUTPUT_VIDEO_DIR = str(_AI_DETECTION_PATH / "output" / "videos")
VIOLATIONS_DIR = str(_AI_DETECTION_PATH / "output" / "screenshots")

# 后端认证配置（用于图片上传和违规上报）
# 使用专用的AI服务账号，避免使用真实用户账号
BACKEND_USERNAME = "ai-detection-service"
BACKEND_PASSWORD = "ai_service_2025"

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

# 全局信号灯状态（从 Java 后端获取）
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

# 信号灯同步配置
SIGNAL_SYNC_INTERVAL = 2  # 从后端获取信号灯状态的间隔（秒）
backend_signal_fetcher = None  # 后台同步任务

# 信号灯数据源模式
# 可选值: 'auto' (优先后端，降级到模拟), 'backend' (仅后端), 'simulation' (仅模拟), 'manual' (手动设置)
signal_source_mode = 'auto'
signal_mode_lock = threading.Lock()

# 当前实际使用的数据源 ('backend' 或 'simulation' 或 'manual')
current_active_source = 'unknown'
last_source_check_time = None


# ==================== 信号灯同步功能 ====================

def fetch_signal_states_from_backend():
    """
    根据当前模式获取信号灯状态

    模式说明：
    - 'auto': 优先从 Java 后端获取，失败时降级到时间模拟
    - 'backend': 仅从 Java 后端获取，失败时不更新
    - 'simulation': 仅使用时间模拟，不调用后端
    - 'manual': 手动设置模式，不自动更新

    信号灯模拟逻辑（60秒周期）：
    - 0-20秒: 南北直行绿灯 + 南北左转红灯 + 东西直行红灯 + 东西左转红灯
    - 20-23秒: 南北黄灯
    - 23-43秒: 东西直行绿灯 + 东西左转绿灯 + 南北直行红灯 + 南北左转红灯
    - 43-46秒: 东西黄灯
    - 46-50秒: 南北左转绿灯
    - 50-53秒: 南北左转黄灯
    - 53-60秒: 等待

    转换为 Python 格式:
    - north_bound: 直行信号
    - south_bound: 直行信号
    - east_bound: 直行信号
    - west_bound: 直行信号
    """
    global current_signal_states, current_left_turn_signals, signal_source_mode, current_active_source, last_source_check_time

    # 获取当前模式
    with signal_mode_lock:
        mode = signal_source_mode

    # 手动模式：不自动更新
    if mode == 'manual':
        current_active_source = 'manual'
        last_source_check_time = datetime.now()
        return True

    # 仅模拟模式：直接跳到模拟逻辑
    if mode == 'simulation':
        current_active_source = 'simulation'
        last_source_check_time = datetime.now()
        _use_time_simulation()
        return True

    # 后端模式或自动模式：尝试从 Java 后端获取
    if mode in ['backend', 'auto']:
        try:
            # 尝试调用 Java 后端获取信号灯状态
            url = f"{BACKEND_BASE_URL}/multi-direction-traffic/intersections/1/status"
            response = requests.get(url, timeout=3)

            if response.status_code == 200:
                # Java 后端可用，从 Java 获取
                data = response.json()

                # 方向映射
                direction_map = {
                    'NORTH': 'north_bound',
                    'SOUTH': 'south_bound',
                    'EAST': 'east_bound',
                    'WEST': 'west_bound'
                }

                new_states = {}
                new_left_turns = {}
                state_changed = False

                for java_dir, py_dir in direction_map.items():
                    if java_dir in data:
                        state_data = data[java_dir]

                        straight_phase = state_data.get('straightPhase', 'RED')
                        left_turn_phase = state_data.get('leftTurnPhase', 'RED')

                        new_straight = straight_phase.lower() if straight_phase else 'red'
                        new_left = left_turn_phase.lower() if left_turn_phase else 'red'

                        if current_signal_states.get(py_dir, '') != new_straight:
                            state_changed = True
                        if current_left_turn_signals.get(py_dir, '') != new_left:
                            state_changed = True

                        new_states[py_dir] = new_straight
                        new_left_turns[py_dir] = new_left

                if new_states:
                    with signal_lock:
                        current_signal_states.update(new_states)
                        current_left_turn_signals.update(new_left_turns)

                    # 记录成功使用后端
                    current_active_source = 'backend'
                    last_source_check_time = datetime.now()

                    if state_changed:
                        print(f"[信号同步] ✅ 从 Java 后端获取 (模式: {mode})")
                        for direction in new_states.keys():
                            straight = new_states[direction]
                            left = new_left_turns[direction]
                            straight_emoji = "🟢" if straight == "green" else "🔴" if straight == "red" else "🟡"
                            left_emoji = "🟢" if left == "green" else "🔴" if left == "red" else "🟡"
                            print(f"  {straight_emoji} {direction}: 直行={straight} | 左转={left}")
                        socketio.emit('traffic', {
                            'signals': convert_to_serializable(current_signal_states.copy()),
                            'leftTurnSignals': convert_to_serializable(current_left_turn_signals.copy())
                        })

                return True

        except Exception as e:
            if mode == 'backend':
                # 仅后端模式：失败时不降级
                current_active_source = 'backend_failed'
                last_source_check_time = datetime.now()
                print(f"[信号同步] ❌ Java 后端不可用 (模式: backend) - {e}")
                return False
            # auto 模式：继续执行下面的模拟逻辑
            print(f"[信号同步] ⚠️  Java 后端不可用，降级到时间模拟 (模式: auto)")

    # auto 模式且后端失败：使用时间模拟
    if mode == 'auto':
        current_active_source = 'simulation'
        last_source_check_time = datetime.now()
        _use_time_simulation()
        return True

    return False


def _use_time_simulation():
    """使用系统时间模拟信号灯状态"""
    global current_signal_states, current_left_turn_signals

    # 使用系统时间模拟信号灯状态
    now = datetime.now()
    seconds_of_minute = now.second + now.microsecond / 1_000_000  # 精确到毫秒
    total_seconds = now.minute * 60 + seconds_of_minute

    # 信号灯周期：60秒
    cycle_position = total_seconds % 60

    # 根据周期位置计算各方向状态
    new_states = {}
    new_left_turns = {}

    if cycle_position < 20:
        # 0-20秒: 南北绿灯
        new_states = {
            'north_bound': 'green',
            'south_bound': 'green',
            'east_bound': 'red',
            'west_bound': 'red'
        }
        new_left_turns = {
            'north_bound': 'red',
            'south_bound': 'red',
            'east_bound': 'red',
            'west_bound': 'red'
        }
    elif cycle_position < 23:
        # 20-23秒: 南北黄灯
        new_states = {
            'north_bound': 'yellow',
            'south_bound': 'yellow',
            'east_bound': 'red',
            'west_bound': 'red'
        }
        new_left_turns = {
            'north_bound': 'red',
            'south_bound': 'red',
            'east_bound': 'red',
            'west_bound': 'red'
        }
    elif cycle_position < 43:
        # 23-43秒: 东西绿灯
        new_states = {
            'north_bound': 'red',
            'south_bound': 'red',
            'east_bound': 'green',
            'west_bound': 'green'
        }
        new_left_turns = {
            'north_bound': 'red',
            'south_bound': 'red',
            'east_bound': 'green',
            'west_bound': 'green'
        }
    elif cycle_position < 46:
        # 43-46秒: 东西黄灯
        new_states = {
            'north_bound': 'red',
            'south_bound': 'red',
            'east_bound': 'yellow',
            'west_bound': 'yellow'
        }
        new_left_turns = {
            'north_bound': 'red',
            'south_bound': 'red',
            'east_bound': 'yellow',
            'west_bound': 'yellow'
        }
    elif cycle_position < 50:
        # 46-50秒: 南北左转绿灯
        new_states = {
            'north_bound': 'red',
            'south_bound': 'red',
            'east_bound': 'red',
            'west_bound': 'red'
        }
        new_left_turns = {
            'north_bound': 'green',
            'south_bound': 'green',
            'east_bound': 'red',
            'west_bound': 'red'
        }
    elif cycle_position < 53:
        # 50-53秒: 南北左转黄灯
        new_states = {
            'north_bound': 'red',
            'south_bound': 'red',
            'east_bound': 'red',
            'west_bound': 'red'
        }
        new_left_turns = {
            'north_bound': 'yellow',
            'south_bound': 'yellow',
            'east_bound': 'red',
            'west_bound': 'red'
        }
    else:
        # 53-60秒: 全红等待
        new_states = {
            'north_bound': 'red',
            'south_bound': 'red',
            'east_bound': 'red',
            'west_bound': 'red'
        }
        new_left_turns = {
            'north_bound': 'red',
            'south_bound': 'red',
            'east_bound': 'red',
            'west_bound': 'red'
        }

    # 检查状态是否变化
    state_changed = False
    for direction in new_states:
        if current_signal_states.get(direction) != new_states[direction]:
            state_changed = True
            break
        if current_left_turn_signals.get(direction) != new_left_turns[direction]:
            state_changed = True
            break

    if state_changed:
        with signal_lock:
            current_signal_states.update(new_states)
            current_left_turn_signals.update(new_left_turns)

        print(f"[信号模拟] {now.strftime('%H:%M:%S')} (周期位置: {cycle_position:.1f}秒)")
        for direction, state in new_states.items():
            emoji = "🟢" if state == "green" else "🔴" if state == "red" else "🟡"
            print(f"  {emoji} {direction}: 直行={state} | 左转={new_left_turns[direction]}")

        # 广播给前端
        socketio.emit('traffic', {
            'signals': convert_to_serializable(current_signal_states.copy()),
            'leftTurnSignals': convert_to_serializable(current_left_turn_signals.copy())
        })


def start_signal_sync_task():
    """启动后台信号灯同步任务"""
    global backend_signal_fetcher

    def sync_loop():
        """同步循环 - 使用精确定时，避免累积误差"""
        import time
        next_run = time.time()

        while True:
            start_time = time.time()
            try:
                # 在独立线程中执行，避免阻塞主循环
                fetch_signal_states_from_backend()
            except Exception as e:
                print(f"[信号同步] 异常: {e}")

            # 计算执行时间
            execution_time = time.time() - start_time
            if execution_time > 1.0:  # 如果执行超过1秒，输出警告
                print(f"[信号同步] ⚠️  同步耗时: {execution_time:.2f}秒")

            # 计算下一次运行时间（精确定时，不累积误差）
            next_run += SIGNAL_SYNC_INTERVAL
            sleep_time = max(0, next_run - time.time())

            if sleep_time > 0:
                time.sleep(sleep_time)

    # 使用标准 threading 启动后台线程
    backend_signal_fetcher = threading.Thread(target=sync_loop, daemon=True)
    backend_signal_fetcher.start()
    print(f"[信号同步] 已启动，每 {SIGNAL_SYNC_INTERVAL} 秒同步一次")


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


@app.route('/screenshots/<filename>', methods=['GET'])
def get_screenshot(filename):
    """
    获取违规快照图片

    Args:
        filename: 图片文件名 (例如: RED_north_bound_1_1234567890.jpg)

    Returns:
        图片文件
    """
    screenshots_dir = _AI_DETECTION_PATH / "output" / "screenshots"
    return send_from_directory(str(screenshots_dir), filename)


@app.route('/api/traffic', methods=['POST'])
def receive_traffic_signal():
    """
    手动设置信号灯状态（测试用）

    注意：正常情况下信号灯状态由后台线程自动从 Java 后端同步，
    此接口仅用于测试或手动覆盖。

    请求体格式:
    {
        "north_bound": "red",
        "south_bound": "green",
        ...
    }
    """
    global current_signal_states, current_left_turn_signals

    try:
        data = request.json

        if not isinstance(data, dict):
            return jsonify({
                "success": False,
                "message": "请求体必须是 JSON 对象"
            }), 400

        # 解析并更新信号灯状态
        signal_states = {}
        for direction in ['north_bound', 'south_bound', 'east_bound', 'west_bound']:
            if direction in data:
                state = data[direction].lower()
                if state in ['red', 'green', 'yellow']:
                    signal_states[direction] = state

        if not signal_states:
            return jsonify({
                "success": False,
                "message": "没有有效的信号灯状态数据"
            }), 400

        # 更新全局状态（线程安全）
        with signal_lock:
            current_signal_states.update(signal_states)

        # 打印状态变化
        print(f"\n[信号灯手动设置] {datetime.now().strftime('%H:%M:%S')}")
        for direction, state in signal_states.items():
            emoji = "🟢" if state == "green" else "🔴" if state == "red" else "🟡"
            print(f"  {emoji} {direction}: {state}")

        # 广播给前端
        socketio.emit('traffic', {
            'signals': convert_to_serializable(current_signal_states.copy()),
            'leftTurnSignals': convert_to_serializable(current_left_turn_signals.copy())
        })

        return jsonify({
            "success": True,
            "message": "信号灯状态已更新（手动设置）",
            "signals": signal_states
        })

    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False,
            "message": f"设置失败: {str(e)}"
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


@app.route('/api/traffic/signal-source-mode', methods=['GET'])
def get_signal_source_mode():
    """
    获取当前信号灯数据源模式

    返回:
    {
        "success": true,
        "mode": "auto",  // 设置的模式: auto/backend/simulation/manual
        "description": "优先后端，降级到模拟",
        "activeSource": "backend",  // 实际使用的数据源: backend/simulation/manual/backend_failed/unknown
        "lastCheckTime": "2025-12-26T17:30:45",
        "availableModes": {
            "auto": "优先后端，降级到模拟",
            "backend": "仅后端",
            "simulation": "仅模拟",
            "manual": "手动设置"
        }
    }
    """
    mode_descriptions = {
        'auto': '优先后端，降级到模拟',
        'backend': '仅后端',
        'simulation': '仅模拟',
        'manual': '手动设置'
    }

    source_descriptions = {
        'backend': '✅ Java 后端',
        'simulation': '🔄 时间模拟',
        'manual': '🎮 手动设置',
        'backend_failed': '❌ 后端失败',
        'unknown': '❓ 未知'
    }

    with signal_mode_lock:
        current_mode = signal_source_mode

    return jsonify({
        "success": True,
        "mode": current_mode,
        "description": mode_descriptions.get(current_mode, "未知模式"),
        "activeSource": current_active_source,
        "activeSourceDescription": source_descriptions.get(current_active_source, "未知"),
        "lastCheckTime": last_source_check_time.isoformat() if last_source_check_time else None,
        "availableModes": mode_descriptions
    })


@app.route('/api/traffic/signal-source-mode', methods=['POST'])
def set_signal_source_mode():
    """
    设置信号灯数据源模式

    请求体:
    {
        "mode": "auto"  // auto/backend/simulation/manual
    }

    模式说明:
    - auto: 优先从 Java 后端获取，失败时降级到时间模拟（默认）
    - backend: 仅从 Java 后端获取，失败时不更新信号
    - simulation: 仅使用时间模拟，不调用后端
    - manual: 手动设置模式，不自动更新（需配合 POST /api/traffic 使用）
    """
    global signal_source_mode

    try:
        data = request.json

        if not data or 'mode' not in data:
            return jsonify({
                "success": False,
                "message": "请求体必须包含 'mode' 字段"
            }), 400

        new_mode = data['mode']
        valid_modes = ['auto', 'backend', 'simulation', 'manual']

        if new_mode not in valid_modes:
            return jsonify({
                "success": False,
                "message": f"无效的模式。可选值: {', '.join(valid_modes)}"
            }), 400

        with signal_mode_lock:
            old_mode = signal_source_mode
            signal_source_mode = new_mode

        print(f"[信号源模式] 已切换: {old_mode} -> {new_mode}")

        return jsonify({
            "success": True,
            "message": f"信号源模式已切换为: {new_mode}",
            "oldMode": old_mode,
            "newMode": new_mode
        })

    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False,
            "message": f"设置失败: {str(e)}"
        }), 500


@app.route('/upload-video', methods=['POST'])
def upload_video():
    """
    上传视频文件并启动实时检测任务

    请求方式: multipart/form-data
    参数:
        - video: 视频文件
        - taskId: 任务ID（可选）
        - intersectionId: 路口ID（可选，默认1）
        - direction: 检测方向（可选，默认SOUTH）
        - roisConfig: ROI配置文件名（可选，默认rois.json，可选rois2.json）

    返回:
    {
        "success": true,
        "taskId": "xxx",
        "videoPath": "/path/to/saved/video.mp4",
        "message": "视频已上传，任务已启动"
    }
    """
    try:
        # 检查是否有文件
        if 'video' not in request.files:
            return jsonify({
                "success": False,
                "message": "没有上传视频文件"
            }), 400

        video_file = request.files['video']
        if video_file.filename == '':
            return jsonify({
                "success": False,
                "message": "文件名为空"
            }), 400

        # 获取参数
        task_id = request.form.get('taskId', f"task_{int(time.time())}")
        intersection_id = int(request.form.get('intersectionId', 1))
        direction = request.form.get('direction', 'SOUTH')
        rois_config = request.form.get('roisConfig', 'rois.json')

        # 验证 ROI 配置文件
        rois_path = str(_AI_DETECTION_PATH / "data" / rois_config)
        if not os.path.exists(rois_path):
            return jsonify({
                "success": False,
                "message": f"ROI配置文件不存在: {rois_config}"
            }), 400

        # 保存视频文件
        video_filename = f"{task_id}_{video_file.filename}"
        video_path = os.path.join(TEMP_VIDEO_DIR, video_filename)
        video_file.save(video_path)

        print(f"✅ 视频已保存: {video_path}")
        print(f"📝 任务ID: {task_id}")
        print(f"🔍 路口ID: {intersection_id}, 方向: {direction}")
        print(f"📐 ROI配置: {rois_config}")

        # 初始化任务状态
        tasks[task_id] = {
            "status": "starting",
            "progress": 0,
            "startTime": datetime.now().isoformat(),
            "violations": [],
            "error": None,
            "videoPath": video_path,
            "roisConfig": rois_config
        }

        # 异步启动处理
        thread = threading.Thread(
            target=process_video_realtime,
            args=(task_id, None, video_path, intersection_id, direction, rois_path)
        )
        thread.daemon = True
        thread.start()

        return jsonify({
            "success": True,
            "taskId": task_id,
            "videoPath": video_path,
            "roisConfig": rois_config,
            "message": "视频已上传，任务已启动。请通过 WebSocket 连接并订阅此任务ID"
        })

    except Exception as e:
        traceback.print_exc()
        return jsonify({
            "success": False,
            "message": f"上传失败: {str(e)}"
        }), 500


@app.route('/start-realtime', methods=['POST'])
def start_realtime_processing():
    """
    启动实时视频处理任务（使用已有的视频URL或路径）

    请求体:
    {
        "taskId": "xxx",
        "videoUrl": "http://...",  // MinIO 视频地址
        "videoPath": "/local/path.mp4",  // 或本地路径（二选一）
        "intersectionId": 1,
        "direction": "SOUTH",
        "roisConfig": "rois.json"  // ROI配置文件名（可选，默认rois.json，可选rois2.json）
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
        rois_config = data.get('roisConfig', 'rois.json')
        
        # 校验参数
        if not video_url and not video_path:
            return jsonify({
                "success": False,
                "message": "缺少 videoUrl 或 videoPath 参数"
            }), 400
        
        # 验证 ROI 配置文件
        rois_path = str(_AI_DETECTION_PATH / "data" / rois_config)
        if not os.path.exists(rois_path):
            return jsonify({
                "success": False,
                "message": f"ROI配置文件不存在: {rois_config}"
            }), 400
        
        # 初始化任务状态
        tasks[task_id] = {
            "status": "starting",
            "progress": 0,
            "startTime": datetime.now().isoformat(),
            "violations": [],
            "error": None,
            "roisConfig": rois_config
        }
        
        # 异步启动处理
        thread = threading.Thread(
            target=process_video_realtime,
            args=(task_id, video_url, video_path, intersection_id, direction, rois_path)
        )
        thread.daemon = True
        thread.start()
        
        return jsonify({
            "success": True,
            "taskId": task_id,
            "roisConfig": rois_config,
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
        "videoName": "car_1_cross.mp4",  // data 目录下的视频文件名
        "roisConfig": "rois.json"  // ROI配置文件名（可选，默认rois.json，可选rois2.json）
    }
    """
    try:
        data = request.json
        video_name = data.get('videoName', 'car_1_cross.mp4')
        rois_config = data.get('roisConfig', 'rois.json')
        video_path = os.path.join('./data', video_name)
        
        if not os.path.exists(video_path):
            return jsonify({
                "success": False,
                "message": f"视频不存在: {video_path}"
            }), 404
        
        # 验证 ROI 配置文件
        rois_path = str(_AI_DETECTION_PATH / "data" / rois_config)
        if not os.path.exists(rois_path):
            return jsonify({
                "success": False,
                "message": f"ROI配置文件不存在: {rois_config}"
            }), 400
        
        task_id = f"test_{int(time.time())}"
        
        # 初始化任务
        tasks[task_id] = {
            "status": "starting",
            "progress": 0,
            "startTime": datetime.now().isoformat(),
            "violations": [],
            "error": None,
            "roisConfig": rois_config
        }
        
        # 异步启动
        thread = threading.Thread(
            target=process_video_realtime,
            args=(task_id, None, video_path, 1, 'SOUTH', rois_path)
        )
        thread.daemon = True
        thread.start()
        
        return jsonify({
            "success": True,
            "taskId": task_id,
            "videoPath": video_path,
            "roisConfig": rois_config,
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
    # 发送连接成功消息
    emit('connected', {'message': 'Connected to AI Realtime Service'})
    # 发送当前信号灯状态给新连接的客户端
    with signal_lock:
        emit('traffic', {
            'signals': convert_to_serializable(current_signal_states.copy()),
            'leftTurnSignals': convert_to_serializable(current_left_turn_signals.copy())
        })


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
                           intersection_id: int, direction: str, 
                           rois_path: str = None):
    """
    实时处理视频并推送帧
    
    Args:
        task_id: 任务ID
        video_url: 视频URL（从MinIO下载）
        video_path: 本地视频路径（直接使用）
        intersection_id: 路口ID
        direction: 方向
        rois_path: ROI配置文件路径（可选，默认使用全局配置）
    """
    # 如果没有指定 ROI 配置，使用默认配置
    if rois_path is None:
        rois_path = ROIS_PATH
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
        print(f"[任务 {task_id}] 使用 ROI 配置: {rois_path}")
        
        tracker = VehicleTracker(model_path=MODEL_PATH, conf_threshold=0.25)
        detector = ViolationDetector(
            rois_path=rois_path,  # 使用传入的 ROI 配置
            screenshot_dir=VIOLATIONS_DIR,
            intersection_id=intersection_id,
            enable_api=True,  # 启用API自动上报
            backend_username=BACKEND_USERNAME,
            backend_password=BACKEND_PASSWORD
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

                    # 添加快照 URL（如果有快照）
                    if v.get('screenshot'):
                        # 从完整路径中提取文件名
                        screenshot_path = Path(v['screenshot'])
                        filename = screenshot_path.name
                        v['screenshotUrl'] = f"http://localhost:5000/screenshots/{filename}"

                    violations_detected.append(v)

                    # 推送违规事件（转换 NumPy 类型）
                    violation_data = convert_to_serializable({
                        'taskId': task_id,
                        'violation': v,
                        'frameNumber': frame_count
                    })
                    socketio.emit('violation', violation_data)
                    print(f"[WebSocket] 推送违规事件: {v.get('type')} Track {v.get('track_id')} @ Frame {frame_count}")
            
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
    stats_text = f"Total: {summary['total_violations']} | Red: {summary['red_light_running']} | Wrong: {summary['wrong_way_driving']} | Across: {summary['lane_change_across_solid_line']} | Waiting: {summary['waiting_area_red_entry']+summary['waiting_area_illegal_exit']}"
    
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

        # 4. 保存图片到临时文件
        import tempfile
        import numpy as np

        # 创建临时文件
        temp_dir = Path(TEMP_VIDEO_DIR)
        temp_dir.mkdir(parents=True, exist_ok=True)
        temp_image_path = temp_dir / f"temp_{int(time.time())}_{image_file.filename}"

        # 保存上传的图片
        image_file.save(str(temp_image_path))

        # 读取图片验证
        image = cv2.imread(str(temp_image_path))
        if image is None:
            temp_image_path.unlink(missing_ok=True)  # 删除临时文件
            return jsonify({
                "success": False,
                "message": "无法解析图片文件"
            }), 400

        # 5. 执行检测
        detector = get_image_detector()
        if detector is None:
            temp_image_path.unlink(missing_ok=True)
            return jsonify({
                "success": False,
                "message": "图片检测器初始化失败"
            }), 500

        result = detector.process_image(
            image_path=str(temp_image_path),
            signal_states=signal_states,
            detect_types=detect_types
        )

        if result is None:
            temp_image_path.unlink(missing_ok=True)
            return jsonify({
                "success": False,
                "message": "图片处理失败"
            }), 500

        # 6. 将标注后的图片转为 base64
        annotated_image = result.get('annotated_image')
        annotated_image_base64 = None

        if annotated_image is not None:
            # 编码为JPEG
            success, buffer = cv2.imencode('.jpg', annotated_image, [cv2.IMWRITE_JPEG_QUALITY, 85])
            if success:
                annotated_image_base64 = base64.b64encode(buffer).decode('utf-8')

        # 检测完成后删除临时文件
        temp_image_path.unlink(missing_ok=True)

        # 7. 返回结果
        return jsonify({
            "success": True,
            "image_name": image_file.filename,
            "image_size": [image.shape[1], image.shape[0]],
            "total_violations": result['total_violations'],
            "violations": convert_to_serializable(result['violations']),
            "annotated_image": annotated_image_base64,  # 新增：标注后的图片(base64)
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

    # 启动信号灯同步任务
    start_signal_sync_task()

    # 启动服务
    socketio.run(app, host='0.0.0.0', port=5000, debug=False, allow_unsafe_werkzeug=True)

