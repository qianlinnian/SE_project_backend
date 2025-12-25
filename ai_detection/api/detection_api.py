"""
Flask API 服务 - TrafficMind 交通智脑
提供图片违规检测接口，供后端调用
"""

import os
import sys
import base64
import json
import cv2
import numpy as np
from pathlib import Path
from flask import Flask, request, jsonify
from flask_cors import CORS
from datetime import datetime

# 添加项目根目录到路径
_API_DIR = Path(__file__).parent
_AI_DETECTION_DIR = _API_DIR.parent
sys.path.insert(0, str(_AI_DETECTION_DIR))

from core.image_violation_detector import ImageViolationDetector

app = Flask(__name__)
CORS(app)  # 允许跨域请求

# 初始化检测器（启动时加载模型）
print("=" * 60)
print("  初始化 AI 违规检测服务")
print("=" * 60)

detector = ImageViolationDetector(
    rois_path=str(_AI_DETECTION_DIR / "data" / "rois.json"),
    model_path=str(_AI_DETECTION_DIR / "yolov8s.pt"),
    screenshot_dir=str(_AI_DETECTION_DIR / "output" / "screenshots"),
    enable_api=True  # 启用后端API集成
)

print("✅ AI 违规检测器初始化成功")
print("=" * 60)


@app.route('/health', methods=['GET'])
def health_check():
    """健康检查接口"""
    return jsonify({
        'status': 'healthy',
        'service': 'TrafficMind AI Detection',
        'version': '1.0.0',
        'model': 'YOLOv8s',
        'timestamp': datetime.now().isoformat()
    })


@app.route('/detect-image', methods=['POST'])
def detect_image():
    """
    检测单张图片（multipart/form-data）

    请求参数:
        - image: 图片文件 (必填)
        - signals: 信号灯状态 JSON (可选)
        - detect_types: 检测类型，逗号分隔 (可选，默认: red_light,lane_change)

    返回:
        {
            "success": true,
            "image_name": "test.jpg",
            "total_violations": 2,
            "red_light_violations": 1,
            "lane_change_violations": 1,
            "violations": [
                {
                    "id": "RED_south_bound_0_1234567890",
                    "type": "red_light_running",
                    "vehicle_index": 0,
                    "direction": "south_bound",
                    "confidence": 0.85,
                    "timestamp": "2024-01-01T12:00:00",
                    "screenshot": "base64编码的标注图片",
                    "screenshot_path": "/path/to/screenshot.jpg"
                }
            ],
            "annotated_image": "base64编码的完整标注图片"
        }
    """
    try:
        # 1. 验证请求
        if 'image' not in request.files:
            return jsonify({
                'success': False,
                'message': '缺少图片文件参数 (image)'
            }), 400

        image_file = request.files['image']
        if image_file.filename == '':
            return jsonify({
                'success': False,
                'message': '未选择文件'
            }), 400

        # 2. 读取图片
        image_bytes = image_file.read()
        nparr = np.frombuffer(image_bytes, np.uint8)
        image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if image is None:
            return jsonify({
                'success': False,
                'message': '无法解码图片'
            }), 400

        # 3. 解析信号灯状态
        signals_str = request.form.get('signals', None)
        signal_states = None
        if signals_str:
            try:
                signal_states = json.loads(signals_str)
            except json.JSONDecodeError:
                return jsonify({
                    'success': False,
                    'message': '信号灯状态JSON格式错误'
                }), 400

        # 4. 解析检测类型
        detect_types_str = request.form.get('detect_types', 'red_light,lane_change')
        detect_types = [t.strip() for t in detect_types_str.split(',')]

        # 5. 执行检测
        print(f"\n📸 检测图片: {image_file.filename}")
        print(f"  - 检测类型: {detect_types}")
        print(f"  - 信号灯状态: {signal_states}")

        result = detector.process_image_data(
            image=image,
            image_name=image_file.filename,
            signal_states=signal_states,
            detect_types=detect_types,
            debug=False
        )

        # 6. 生成标注图片
        annotated_image = draw_violations_on_image(image, result['violations'])

        # 7. 将标注图片转为 Base64
        _, buffer = cv2.imencode('.jpg', annotated_image)
        annotated_base64 = base64.b64encode(buffer).decode('utf-8')

        # 8. 将每个违规的截图也转为 Base64
        for violation in result['violations']:
            screenshot_path = violation.get('screenshot')
            if screenshot_path and os.path.exists(screenshot_path):
                with open(screenshot_path, 'rb') as f:
                    screenshot_base64 = base64.b64encode(f.read()).decode('utf-8')
                    violation['screenshot_base64'] = screenshot_base64

        # 9. 构建响应
        response = {
            'success': True,
            'image_name': image_file.filename,
            'image_size': f"{image.shape[1]}x{image.shape[0]}",
            'total_violations': result['total_violations'],
            'red_light_violations': result['red_light_violations'],
            'lane_change_violations': result['lane_change_violations'],
            'violations': result['violations'],
            'annotated_image': annotated_base64,  # 完整标注图片
            'timestamp': datetime.now().isoformat()
        }

        print(f"✅ 检测完成: 发现 {result['total_violations']} 个违规")

        return jsonify(response)

    except Exception as e:
        print(f"❌ 检测失败: {type(e).__name__}: {e}")
        return jsonify({
            'success': False,
            'message': f'检测失败: {str(e)}'
        }), 500


@app.route('/detect-image-base64', methods=['POST'])
def detect_image_base64():
    """
    检测单张图片（JSON + Base64编码）

    请求体:
        {
            "image": "base64编码的图片数据",
            "signals": {"north_bound": "red", ...},  // 可选
            "detect_types": ["red_light", "lane_change"]  // 可选
        }
    """
    try:
        data = request.get_json()

        # 1. 验证请求
        if 'image' not in data:
            return jsonify({
                'success': False,
                'message': '缺少图片数据 (image)'
            }), 400

        # 2. 解码 Base64 图片
        try:
            image_base64 = data['image']
            # 移除 data:image/jpeg;base64, 前缀（如果有）
            if ',' in image_base64:
                image_base64 = image_base64.split(',')[1]

            image_bytes = base64.b64decode(image_base64)
            nparr = np.frombuffer(image_bytes, np.uint8)
            image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

            if image is None:
                return jsonify({
                    'success': False,
                    'message': '无法解码图片'
                }), 400

        except Exception as e:
            return jsonify({
                'success': False,
                'message': f'Base64解码失败: {str(e)}'
            }), 400

        # 3. 解析参数
        signal_states = data.get('signals', None)
        detect_types = data.get('detect_types', ['red_light', 'lane_change'])

        # 4. 执行检测
        result = detector.process_image_data(
            image=image,
            image_name='base64_image.jpg',
            signal_states=signal_states,
            detect_types=detect_types,
            debug=False
        )

        # 5. 生成标注图片
        annotated_image = draw_violations_on_image(image, result['violations'])
        _, buffer = cv2.imencode('.jpg', annotated_image)
        annotated_base64 = base64.b64encode(buffer).decode('utf-8')

        # 6. 违规截图转 Base64
        for violation in result['violations']:
            screenshot_path = violation.get('screenshot')
            if screenshot_path and os.path.exists(screenshot_path):
                with open(screenshot_path, 'rb') as f:
                    violation['screenshot_base64'] = base64.b64encode(f.read()).decode('utf-8')

        # 7. 构建响应
        response = {
            'success': True,
            'total_violations': result['total_violations'],
            'red_light_violations': result['red_light_violations'],
            'lane_change_violations': result['lane_change_violations'],
            'violations': result['violations'],
            'annotated_image': annotated_base64,
            'timestamp': datetime.now().isoformat()
        }

        return jsonify(response)

    except Exception as e:
        return jsonify({
            'success': False,
            'message': f'检测失败: {str(e)}'
        }), 500


def draw_violations_on_image(image, violations):
    """
    在图片上标注所有违规

    Args:
        image: 原始图片
        violations: 违规列表

    Returns:
        标注后的图片
    """
    annotated = image.copy()

    for violation in violations:
        bbox = violation.get('bbox')
        if not bbox:
            continue

        x1, y1, x2, y2 = bbox

        # 根据违规类型选择颜色
        if violation['type'] == 'red_light_running':
            color = (0, 0, 255)  # 红色
            label = f"闯红灯 {violation['confidence']:.2f}"
        elif violation['type'] == 'lane_change_across_solid_line':
            color = (0, 165, 255)  # 橙色
            label = f"压线 {violation['confidence']:.2f}"
        else:
            color = (255, 0, 0)  # 蓝色
            label = f"违规 {violation['confidence']:.2f}"

        # 绘制边界框
        cv2.rectangle(annotated, (x1, y1), (x2, y2), color, 3)

        # 绘制标签背景
        (text_width, text_height), baseline = cv2.getTextSize(
            label, cv2.FONT_HERSHEY_SIMPLEX, 0.6, 2
        )
        cv2.rectangle(
            annotated,
            (x1, y1 - text_height - baseline - 5),
            (x1 + text_width, y1),
            color,
            -1
        )

        # 绘制标签文字
        cv2.putText(
            annotated,
            label,
            (x1, y1 - baseline - 2),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.6,
            (255, 255, 255),
            2
        )

        # 绘制车辆位置点
        location = violation.get('location')
        if location:
            cv2.circle(annotated, location, 8, (0, 255, 255), -1)

    return annotated


if __name__ == '__main__':
    print("\n" + "=" * 60)
    print("  启动 TrafficMind AI 检测服务")
    print("=" * 60)
    print("  监听地址: http://0.0.0.0:5000")
    print("  接口列表:")
    print("    - GET  /health              健康检查")
    print("    - POST /detect-image        检测图片（multipart）")
    print("    - POST /detect-image-base64 检测图片（Base64）")
    print("=" * 60 + "\n")

    app.run(host='0.0.0.0', port=5000, debug=True)
