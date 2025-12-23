"""
AI 实时服务测试脚本

测试方式:
1. 先启动 AI 服务: python ai_realtime_service.py
2. 新开终端运行此脚本: python test_realtime_service.py

此脚本会:
1. 测试 HTTP API（健康检查、启动任务）
2. 连接 WebSocket 接收实时帧
3. 显示接收到的帧信息
"""

import requests
import socketio
import time
import sys
import base64
from datetime import datetime

# 配置
AI_SERVICE_URL = "http://localhost:5000"
TEST_VIDEO = "car_1_cross.mp4"  # data 目录下的测试视频


def test_health_check():
    """测试健康检查"""
    print("\n" + "=" * 50)
    print("1️⃣  测试健康检查")
    print("=" * 50)
    
    try:
        response = requests.get(f"{AI_SERVICE_URL}/health", timeout=5)
        if response.status_code == 200:
            data = response.json()
            print(f"✅ 服务状态: {data.get('status')}")
            print(f"   服务名称: {data.get('service')}")
            print(f"   版本: {data.get('version')}")
            print(f"   WebSocket: {data.get('websocket')}")
            return True
        else:
            print(f"❌ HTTP 状态码: {response.status_code}")
            return False
    except requests.exceptions.ConnectionError:
        print("❌ 无法连接到 AI 服务")
        print("   请先启动: python ai_realtime_service.py")
        return False
    except Exception as e:
        print(f"❌ 错误: {e}")
        return False


def test_websocket_with_local_video():
    """使用 WebSocket 测试本地视频处理"""
    print("\n" + "=" * 50)
    print("2️⃣  测试 WebSocket 实时推流")
    print("=" * 50)
    
    # 创建 SocketIO 客户端
    sio = socketio.Client()
    
    frame_count = 0
    violations = []
    start_time = None
    task_id = None
    
    @sio.on('connect')
    def on_connect():
        print("✅ WebSocket 已连接")
    
    @sio.on('disconnect')
    def on_disconnect():
        print("⚠️  WebSocket 已断开")
    
    @sio.on('connected')
    def on_server_connected(data):
        print(f"   服务器消息: {data.get('message')}")
    
    @sio.on('frame')
    def on_frame(data):
        nonlocal frame_count, start_time
        if start_time is None:
            start_time = time.time()
        
        frame_count += 1
        progress = data.get('progress', 0)
        frame_num = data.get('frameNumber', 0)
        image_size = len(data.get('image', ''))
        
        # 每收到 10 帧打印一次
        if frame_count % 10 == 0 or frame_count == 1:
            elapsed = time.time() - start_time
            fps = frame_count / elapsed if elapsed > 0 else 0
            print(f"   📹 帧 #{frame_num} | 进度: {progress}% | 已接收: {frame_count} 帧 | FPS: {fps:.1f} | 图片大小: {image_size//1024}KB")
    
    @sio.on('violation')
    def on_violation(data):
        violations.append(data)
        v = data.get('violation', {})
        print(f"   🚨 违规检测! 类型: {v.get('type')} | 车辆ID: {v.get('track_id')} | 帧: {data.get('frameNumber')}")
    
    @sio.on('complete')
    def on_complete(data):
        nonlocal task_id
        result = data.get('result', {})
        print("\n" + "=" * 50)
        print("✅ 处理完成!")
        print("=" * 50)
        print(f"   总帧数: {result.get('totalFrames')}")
        print(f"   处理时间: {result.get('elapsedTime')}秒")
        print(f"   实际FPS: {result.get('actualFps')}")
        print(f"   检测到违规: {len(violations)} 条")
        print(f"   输出视频: {result.get('outputVideoPath')}")
        
        summary = result.get('violationSummary', {})
        print(f"\n   📊 违规统计:")
        print(f"      - 闯红灯: {summary.get('red_light_running', 0)}")
        print(f"      - 逆行: {summary.get('wrong_way_driving', 0)}")
        print(f"      - 跨实线: {summary.get('lane_change_across_solid_line', 0)}")
        
        # 断开连接
        sio.disconnect()
    
    @sio.on('error')
    def on_error(data):
        print(f"❌ 错误: {data.get('message')}")
        sio.disconnect()
    
    try:
        # 连接 WebSocket
        print(f"正在连接 WebSocket: {AI_SERVICE_URL}")
        sio.connect(AI_SERVICE_URL, transports=['websocket', 'polling'])
        
        time.sleep(0.5)  # 等待连接建立
        
        # 启动本地视频测试任务
        print(f"\n启动本地视频测试: {TEST_VIDEO}")
        response = requests.post(
            f"{AI_SERVICE_URL}/test-local",
            json={"videoName": TEST_VIDEO},
            timeout=10
        )
        
        if response.status_code == 200:
            data = response.json()
            if data.get('success'):
                task_id = data.get('taskId')
                print(f"✅ 任务已启动: {task_id}")
                print(f"   视频路径: {data.get('videoPath')}")
                print("\n等待接收实时帧...")
            else:
                print(f"❌ 启动失败: {data.get('message')}")
                sio.disconnect()
                return
        else:
            print(f"❌ HTTP 错误: {response.status_code}")
            sio.disconnect()
            return
        
        # 等待处理完成
        sio.wait()
        
    except socketio.exceptions.ConnectionError as e:
        print(f"❌ WebSocket 连接失败: {e}")
    except Exception as e:
        print(f"❌ 错误: {e}")
        import traceback
        traceback.print_exc()
    finally:
        if sio.connected:
            sio.disconnect()


def main():
    print("\n" + "🚦" * 20)
    print("  TrafficMind AI 实时服务测试")
    print("🚦" * 20)
    
    # 1. 健康检查
    if not test_health_check():
        print("\n⚠️  请先启动 AI 服务:")
        print("   cd ai_detection")
        print("   python ai_realtime_service.py")
        return 1
    
    # 2. WebSocket 测试
    test_websocket_with_local_video()
    
    print("\n" + "=" * 50)
    print("测试完成!")
    print("=" * 50 + "\n")
    
    return 0


if __name__ == "__main__":
    sys.exit(main())

