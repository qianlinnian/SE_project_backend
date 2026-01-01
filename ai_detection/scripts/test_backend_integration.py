"""
后端集成测试脚本
用于测试违规检测模块与后端API的集成
"""

import os
import sys

# 添加父目录到 Python 路径，确保可以导入 api 模块
_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_AI_DETECTION_DIR = os.path.dirname(_SCRIPT_DIR)
if _AI_DETECTION_DIR not in sys.path:
    sys.path.insert(0, _AI_DETECTION_DIR)

from datetime import datetime
from api.backend_api_client import BackendAPIClient


def print_section(title):
    """打印分隔线"""
    print("\n" + "=" * 70)
    print(f"  {title}")
    print("=" * 70)


def test_backend_connection():
    """测试1: 后端连接测试"""
    print_section("测试1: 后端连接")

    client = BackendAPIClient("http://localhost:8081/api")

    # 健康检查
    print("\n[1.1] 健康检查...")
    is_healthy = client.health_check()

    if not is_healthy:
        print("\n后端服务不可用!")
        print("   请确保后端已启动在 http://localhost:8081")
        print("   提示: 可能需要先启动后端服务")
        return False

    print("后端连接成功!")
    return True


def test_signal_status():
    """测试2: 信号灯状态查询"""
    print_section("测试2: 信号灯状态查询")

    client = BackendAPIClient("http://localhost:8081/api")

    # 测试所有方向的信号灯
    directions = ['EAST', 'SOUTH', 'WEST', 'NORTH']
    turn_types = ['STRAIGHT', 'LEFT_TURN']

    print("\n正在查询路口1的信号灯状态...")
    for direction in directions:
        for turn_type in turn_types:
            status = client.get_signal_status(1, direction, turn_type)
            symbol = "🟢" if status == "GREEN" else "🔴" if status == "RED" else "🟡"
            print(f"  {symbol} {direction:6s} {turn_type:12s}: {status or '查询失败'}")

    print("\n信号灯状态查询完成")


def test_violation_validation():
    """测试3: 违规验证"""
    print_section("测试3: 违规验证")

    client = BackendAPIClient("http://localhost:8081/api")

    # 测试闯红灯验证
    print("\n[3.1] 测试闯红灯验证...")
    is_violation = client.validate_violation(1, 'SOUTH', 'STRAIGHT', 'RED_LIGHT')
    print(f"  南向直行闯红灯: {'构成违规' if is_violation else '不构成违规'}")

    # 测试逆行验证
    print("\n[3.2] 测试逆行验证...")
    is_violation = client.validate_violation(1, 'SOUTH', 'STRAIGHT', 'WRONG_WAY')
    print(f"  南向逆行: {'构成违规' if is_violation else '不构成违规'}")

    print("\n违规验证测试完成")


def test_violation_report():
    """测试4: 违规上报"""
    print_section("测试4: 违规上报")

    client = BackendAPIClient("http://localhost:8081/api")

    # 准备测试违规数据
    test_violations = [
        {
            'name': '闯红灯',
            'data': {
                'intersectionId': 1,
                'direction': 'SOUTH',
                'turnType': 'STRAIGHT',
                'plateNumber': '京A12345',
                'violationType': 'RED_LIGHT',
                'imageUrl': 'file:///violations/test_red_light.jpg',
                'aiConfidence': 0.95,
                'occurredAt': datetime.now().isoformat()
            }
        },
        {
            'name': '逆行',
            'data': {
                'intersectionId': 1,
                'direction': 'NORTH',
                'turnType': 'STRAIGHT',
                'plateNumber': '京B54321',
                'violationType': 'WRONG_WAY',
                'imageUrl': 'file:///violations/test_wrong_way.jpg',
                'aiConfidence': 0.92,
                'occurredAt': datetime.now().isoformat()
            }
        },
        {
            'name': '跨实线变道',
            'data': {
                'intersectionId': 1,
                'direction': 'EAST',
                'turnType': 'STRAIGHT',
                'plateNumber': '沪C88888',
                'violationType': 'CROSS_SOLID_LINE',
                'imageUrl': 'file:///violations/test_cross_line.jpg',
                'aiConfidence': 0.88,
                'occurredAt': datetime.now().isoformat()
            }
        }
    ]

    # 上报测试违规
    print("\n正在上报测试违规...")
    success_count = 0
    for i, violation in enumerate(test_violations, 1):
        print(f"\n[4.{i}] 上报 {violation['name']}...")
        violation_id = client.report_violation(violation['data'])

        if violation_id:
            print(f"  上报成功! 违规ID: {violation_id}")
            success_count += 1
        else:
            print(f"  上报失败!")

    print(f"\n违规上报测试完成 ({success_count}/{len(test_violations)} 成功)")


def test_intersection_status():
    """测试5: 路口整体状态"""
    print_section("测试5: 路口整体状态")

    client = BackendAPIClient("http://localhost:8081/api")

    print("\n正在获取路口1的整体状态...")
    status = client.get_intersection_status(1)

    if status:
        print("\n路口状态:")
        for direction, info in status.items():
            if isinstance(info, dict):
                print(f"\n  {direction}:")
                print(f"    直行: {info.get('straightPhase', 'N/A')} (剩余 {info.get('straightRemaining', 0)}s)")
                print(f"    左转: {info.get('leftTurnPhase', 'N/A')} (剩余 {info.get('leftTurnRemaining', 0)}s)")
                print(f"    右转: {info.get('rightTurnPhase', 'N/A')} (剩余 {info.get('rightTurnRemaining', 0)}s)")

        print("\n路口状态查询成功")
    else:
        print("路口状态查询失败")


def main():
    """主测试流程"""
    print("\n" + "🚦" * 35)
    print("  TrafficMind 后端集成测试")
    print("🚦" * 35)

    # 测试1: 后端连接
    if not test_backend_connection():
        print("\n⚠️  后端连接失败，跳过后续测试")
        print("   请先启动后端服务，然后重新运行此脚本")
        return 1

    # 测试2: 信号灯状态
    try:
        test_signal_status()
    except Exception as e:
        print(f"\n信号灯状态测试失败: {e}")

    # 测试3: 违规验证
    try:
        test_violation_validation()
    except Exception as e:
        print(f"\n违规验证测试失败: {e}")

    # 测试4: 违规上报
    try:
        test_violation_report()
    except Exception as e:
        print(f"\n违规上报测试失败: {e}")

    # 测试5: 路口状态
    try:
        test_intersection_status()
    except Exception as e:
        print(f"\n路口状态测试失败: {e}")

    # 总结
    print_section("测试总结")
    print("\n所有测试已完成!")
    print("\n下一步:")
    print("   1. 检查后端数据库，确认违规记录已保存")
    print("   2. 修改 violation_detector.py，集成 API 客户端")
    print("   3. 运行完整的视频检测流程测试")
    print("\n" + "🚦" * 35 + "\n")

    return 0


if __name__ == "__main__":
    sys.exit(main())
