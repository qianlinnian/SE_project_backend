"""
测试AI检测服务获取LLM交通数据
演示如何在违规检测中使用LLM提供的路口实时信息
"""

import sys
from pathlib import Path

# 添加项目根目录到路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from api.backend_api_client import BackendAPIClient


def test_get_all_traffic_data():
    """测试获取所有路口的LLM数据"""
    print("\n" + "="*60)
    print("测试1: 获取LLM所有路口数据")
    print("="*60)

    client = BackendAPIClient()

    # 获取完整交通数据
    traffic_data = client.get_llm_traffic_data()

    if traffic_data:
        print(f"\n✅ 成功获取LLM交通数据!")
        print(f"   - 模拟时间戳: {traffic_data.get('timestamp')}")
        print(f"   - 当前步数: {traffic_data.get('step')}")
        print(f"   - 路网: {traffic_data.get('roadnet')}")
        print(f"   - 控制模式: {traffic_data.get('control_mode')}")
        print(f"   - 路口总数: {traffic_data.get('total_intersections')}")

        intersections = traffic_data.get('intersections', [])
        print(f"\n   包含 {len(intersections)} 个路口的详细数据:")

        for i, intersection in enumerate(intersections[:3]):  # 只显示前3个
            print(f"\n   路口{intersection.get('id')}:")
            print(f"      信号相位: {intersection.get('signal_phase')}")
            print(f"      排队长度: {intersection.get('queue_length')}")
            print(f"      车辆总数: {intersection.get('vehicle_count')}")

            lanes = intersection.get('lanes', {})
            print(f"      车道数量: {len(lanes)}")
            for lane_name, lane_data in list(lanes.items())[:2]:  # 只显示前2个车道
                print(f"         {lane_name}: 排队{lane_data.get('queue_len')}, 格子{lane_data.get('cells')}")
    else:
        print("\n❌ 获取LLM交通数据失败")
        print("   可能原因:")
        print("   1. LLM服务器还未发送数据")
        print("   2. 后端服务未启动")
        print("   3. Redis中没有缓存数据")


def test_get_single_intersection_data():
    """测试获取单个路口的LLM数据"""
    print("\n" + "="*60)
    print("测试2: 获取指定路口数据")
    print("="*60)

    client = BackendAPIClient()
    intersection_id = 0  # 测试路口0

    # 获取单个路口数据
    intersection_data = client.get_intersection_llm_data(intersection_id)

    if intersection_data:
        print(f"\n✅ 成功获取路口{intersection_id}数据!")
        print(f"   ID: {intersection_data.get('id')}")
        print(f"   信号相位: {intersection_data.get('signal_phase')}")
        print(f"   相位编码: {intersection_data.get('phase_code')}")
        print(f"   排队长度: {intersection_data.get('queue_length')}")
        print(f"   车辆总数: {intersection_data.get('vehicle_count')}")

        lanes = intersection_data.get('lanes', {})
        print(f"\n   车道详情 (共{len(lanes)}个车道):")
        for lane_name, lane_data in lanes.items():
            print(f"      {lane_name}:")
            print(f"         格子占用: {lane_data.get('cells')}")
            print(f"         排队长度: {lane_data.get('queue_len')}")
    else:
        print(f"\n❌ 获取路口{intersection_id}数据失败")


def demo_use_in_violation_detection():
    """演示如何在违规检测中使用LLM数据"""
    print("\n" + "="*60)
    print("演示: 在违规检测中使用LLM数据")
    print("="*60)

    client = BackendAPIClient()
    intersection_id = 0

    # 场景：检测到一辆车可能闯红灯
    print(f"\n📹 场景: AI检测到车辆可能违规...")

    # 获取当前路口的实时数据
    intersection_data = client.get_intersection_llm_data(intersection_id)

    if intersection_data:
        signal_phase = intersection_data.get('signal_phase')
        print(f"   当前路口{intersection_id}信号相位: {signal_phase}")

        # 解析信号相位判断是否真的闯红灯
        # ETWT = 东西直行通行 (East-West Through)
        # NSNL = 南北左转通行 (North-South Left)
        # 等等...

        if signal_phase:
            print(f"\n   📊 信号相位解析:")
            if 'ET' in signal_phase or 'WT' in signal_phase:
                print(f"      ✅ 东西方向直行通行中")
            if 'NT' in signal_phase or 'ST' in signal_phase:
                print(f"      ✅ 南北方向直行通行中")
            if 'EL' in signal_phase or 'WL' in signal_phase:
                print(f"      ✅ 东西方向左转通行中")
            if 'NL' in signal_phase or 'SL' in signal_phase:
                print(f"      ✅ 南北方向左转通行中")

        # 获取车道占用情况
        lanes = intersection_data.get('lanes', {})
        queue_length = intersection_data.get('queue_length')
        vehicle_count = intersection_data.get('vehicle_count')

        print(f"\n   📈 交通状况:")
        print(f"      排队长度: {queue_length}")
        print(f"      车辆总数: {vehicle_count}")

        # 检查拥堵情况
        if queue_length > 10:
            print(f"      ⚠️ 路口拥堵严重，排队{queue_length}辆")
        elif queue_length > 5:
            print(f"      ⚠️ 路口轻度拥堵，排队{queue_length}辆")
        else:
            print(f"      ✅ 路口畅通")

        print(f"\n   💡 AI检测建议:")
        print(f"      根据当前信号相位和车道占用情况，")
        print(f"      可以更准确地判断车辆是否真的违规。")
        print(f"      例如：如果南北方向是红灯，但LLM显示NT车道有车辆通过，")
        print(f"      可以交叉验证AI视觉检测结果。")


def demo_signal_phase_mapping():
    """演示信号相位映射表"""
    print("\n" + "="*60)
    print("LLM信号相位编码说明")
    print("="*60)

    phase_mapping = {
        'ETWT': '东西直行通行 (East-West Through)',
        'NSNL': '南北左转通行 (North-South Left Turn)',
        'NTWT': '南北直行通行 (North-South Through)',
        'EWEL': '东西左转通行 (East-West Left Turn)',
        'NSNT': '南北直行通行',
        'EWET': '东西直行通行',
    }

    print("\n常见信号相位:")
    for code, description in phase_mapping.items():
        print(f"   {code:8s} -> {description}")

    print("\n车道编码说明:")
    lane_codes = {
        'NT': '北向直行 (North Through)',
        'NL': '北向左转 (North Left)',
        'NR': '北向右转 (North Right)',
        'ST': '南向直行 (South Through)',
        'SL': '南向左转 (South Left)',
        'SR': '南向右转 (South Right)',
        'ET': '东向直行 (East Through)',
        'EL': '东向左转 (East Left)',
        'ER': '东向右转 (East Right)',
        'WT': '西向直行 (West Through)',
        'WL': '西向左转 (West Left)',
        'WR': '西向右转 (West Right)',
    }

    for code, description in lane_codes.items():
        print(f"   {code:3s} -> {description}")


if __name__ == "__main__":
    print("\n🚦 TrafficMind AI检测 - LLM数据集成测试")
    print("="*60)

    # 运行测试
    test_get_all_traffic_data()
    test_get_single_intersection_data()
    demo_use_in_violation_detection()
    demo_signal_phase_mapping()

    print("\n" + "="*60)
    print("✅ 测试完成!")
    print("="*60)

    print("\n📝 使用说明:")
    print("   1. 在ViolationDetector中调用 backend_client.get_intersection_llm_data()")
    print("   2. 获取当前路口的信号相位、车道占用等实时信息")
    print("   3. 结合AI视觉检测结果，进行交叉验证")
    print("   4. 提高违规检测的准确性，减少误报")
    print()
