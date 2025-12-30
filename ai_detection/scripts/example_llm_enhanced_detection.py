"""
LLM增强的违规检测示例
演示如何结合LLM交通数据和AI视觉检测进行更准确的违规判断
"""

import sys
from pathlib import Path
from typing import Dict, Optional

# 添加项目根目录到路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from api.backend_api_client import BackendAPIClient


class LLMEnhancedViolationDetector:
    """
    LLM增强的违规检测器
    结合AI视觉检测和LLM交通数据进行双重验证
    """

    def __init__(self, intersection_id: int = 0):
        """
        初始化检测器

        Args:
            intersection_id: 路口ID
        """
        self.intersection_id = intersection_id
        self.backend_client = BackendAPIClient()
        self.llm_data_cache = None
        self.cache_timestamp = None

    def get_current_llm_data(self, force_refresh: bool = False) -> Optional[Dict]:
        """
        获取当前路口的LLM数据（带缓存）

        Args:
            force_refresh: 是否强制刷新缓存

        Returns:
            路口数据字典
        """
        # 如果有缓存且不强制刷新，使用缓存
        if self.llm_data_cache and not force_refresh:
            return self.llm_data_cache

        # 获取新数据
        data = self.backend_client.get_intersection_llm_data(self.intersection_id)
        if data:
            self.llm_data_cache = data
            self.cache_timestamp = data.get('timestamp')

        return data

    def parse_signal_phase(self, signal_phase: str) -> Dict[str, str]:
        """
        解析信号相位，返回各方向的通行状态

        Args:
            signal_phase: 信号相位编码 (如 "ETWT", "NSNL")

        Returns:
            各方向通行状态字典
            {
                'north_through': 'GREEN',
                'north_left': 'RED',
                'south_through': 'GREEN',
                ...
            }
        """
        # 初始化所有方向为红灯
        status = {
            'north_through': 'RED',
            'north_left': 'RED',
            'north_right': 'RED',
            'south_through': 'RED',
            'south_left': 'RED',
            'south_right': 'RED',
            'east_through': 'RED',
            'east_left': 'RED',
            'east_right': 'RED',
            'west_through': 'RED',
            'west_left': 'RED',
            'west_right': 'RED',
        }

        if not signal_phase:
            return status

        # 解析相位编码
        # NT = North Through, NL = North Left
        # ET = East Through, EL = East Left
        # 等等...
        phase = signal_phase.upper()

        if 'NT' in phase:
            status['north_through'] = 'GREEN'
        if 'NL' in phase:
            status['north_left'] = 'GREEN'
        if 'NR' in phase:
            status['north_right'] = 'GREEN'

        if 'ST' in phase:
            status['south_through'] = 'GREEN'
        if 'SL' in phase:
            status['south_left'] = 'GREEN'
        if 'SR' in phase:
            status['south_right'] = 'GREEN'

        if 'ET' in phase:
            status['east_through'] = 'GREEN'
        if 'EL' in phase:
            status['east_left'] = 'GREEN'
        if 'ER' in phase:
            status['east_right'] = 'GREEN'

        if 'WT' in phase:
            status['west_through'] = 'GREEN'
        if 'WL' in phase:
            status['west_left'] = 'GREEN'
        if 'WR' in phase:
            status['west_right'] = 'GREEN'

        return status

    def verify_red_light_violation(self, direction: str, turn_type: str) -> Dict:
        """
        验证闯红灯违规（结合LLM数据）

        Args:
            direction: 车辆行驶方向 (NORTH/SOUTH/EAST/WEST)
            turn_type: 转弯类型 (STRAIGHT/LEFT_TURN/RIGHT_TURN)

        Returns:
            验证结果字典
            {
                'is_violation': bool,  # 是否构成违规
                'confidence': float,   # 置信度
                'reason': str,         # 判断原因
                'llm_phase': str,      # LLM信号相位
                'signal_status': str   # 该方向信号灯状态
            }
        """
        result = {
            'is_violation': False,
            'confidence': 0.0,
            'reason': '',
            'llm_phase': None,
            'signal_status': None
        }

        # 获取LLM数据
        llm_data = self.get_current_llm_data()
        if not llm_data:
            result['reason'] = 'LLM数据不可用，无法验证'
            result['confidence'] = 0.5  # 不确定
            return result

        # 获取信号相位
        signal_phase = llm_data.get('signal_phase')
        result['llm_phase'] = signal_phase

        if not signal_phase:
            result['reason'] = 'LLM未提供信号相位数据'
            result['confidence'] = 0.5
            return result

        # 解析信号状态
        signal_status = self.parse_signal_phase(signal_phase)

        # 映射方向和转弯类型到信号状态键
        direction_map = {
            'NORTH': 'north',
            'SOUTH': 'south',
            'EAST': 'east',
            'WEST': 'west'
        }

        turn_map = {
            'STRAIGHT': 'through',
            'LEFT_TURN': 'left',
            'RIGHT_TURN': 'right'
        }

        direction_key = direction_map.get(direction.upper(), 'north')
        turn_key = turn_map.get(turn_type.upper(), 'through')
        status_key = f"{direction_key}_{turn_key}"

        current_signal = signal_status.get(status_key, 'RED')
        result['signal_status'] = current_signal

        # 判断是否违规
        if current_signal == 'RED':
            result['is_violation'] = True
            result['confidence'] = 0.9
            result['reason'] = f'{direction} {turn_type} 信号灯为红灯，车辆通过构成闯红灯'
        else:
            result['is_violation'] = False
            result['confidence'] = 0.9
            result['reason'] = f'{direction} {turn_type} 信号灯为绿灯，车辆合法通过'

        return result

    def get_lane_occupancy(self, lane_name: str) -> Optional[Dict]:
        """
        获取指定车道的占用情况

        Args:
            lane_name: 车道名称 (NT/NL/ST/SL/ET/EL/WT/WL等)

        Returns:
            车道数据字典
            {
                'cells': [1,2,0,0],  # 格子占用 (0=空, 1=有车, 2=多辆车)
                'queue_len': 3       # 排队长度
            }
        """
        llm_data = self.get_current_llm_data()
        if not llm_data:
            return None

        lanes = llm_data.get('lanes', {})
        return lanes.get(lane_name.upper())

    def check_traffic_congestion(self) -> Dict:
        """
        检查路口拥堵情况

        Returns:
            拥堵信息字典
            {
                'is_congested': bool,
                'level': str,  # 'CLEAR'/'LIGHT'/'MODERATE'/'SEVERE'
                'queue_length': int,
                'vehicle_count': int
            }
        """
        llm_data = self.get_current_llm_data()
        if not llm_data:
            return {
                'is_congested': False,
                'level': 'UNKNOWN',
                'queue_length': 0,
                'vehicle_count': 0
            }

        queue_length = llm_data.get('queue_length', 0)
        vehicle_count = llm_data.get('vehicle_count', 0)

        # 判断拥堵级别
        if queue_length >= 15:
            level = 'SEVERE'
            is_congested = True
        elif queue_length >= 10:
            level = 'MODERATE'
            is_congested = True
        elif queue_length >= 5:
            level = 'LIGHT'
            is_congested = True
        else:
            level = 'CLEAR'
            is_congested = False

        return {
            'is_congested': is_congested,
            'level': level,
            'queue_length': queue_length,
            'vehicle_count': vehicle_count
        }


# ==================== 使用示例 ====================

def example_red_light_detection():
    """示例：闯红灯检测"""
    print("\n" + "="*70)
    print("示例1: LLM增强的闯红灯检测")
    print("="*70)

    detector = LLMEnhancedViolationDetector(intersection_id=0)

    # 场景：AI视觉检测到南向车辆直行通过路口
    print("\n📹 AI视觉检测: 发现南向直行车辆 (车牌: 京A12345)")

    # 使用LLM数据验证
    verification = detector.verify_red_light_violation(
        direction='SOUTH',
        turn_type='STRAIGHT'
    )

    print(f"\n🔍 LLM数据验证结果:")
    print(f"   当前信号相位: {verification['llm_phase']}")
    print(f"   南向直行信号: {verification['signal_status']}")
    print(f"   是否违规: {'✅ 是' if verification['is_violation'] else '❌ 否'}")
    print(f"   置信度: {verification['confidence']:.1%}")
    print(f"   判断原因: {verification['reason']}")

    if verification['is_violation']:
        print(f"\n⚠️ 确认违规! 可以自动上报")
    else:
        print(f"\n✅ 合法通行，不构成违规")


def example_lane_occupancy_check():
    """示例：车道占用检查"""
    print("\n" + "="*70)
    print("示例2: 查询车道占用情况")
    print("="*70)

    detector = LLMEnhancedViolationDetector(intersection_id=0)

    # 检查北向直行车道
    lane_data = detector.get_lane_occupancy('NT')

    if lane_data:
        print(f"\n📊 北向直行车道 (NT) 占用情况:")
        print(f"   格子占用: {lane_data.get('cells')}")
        print(f"   排队长度: {lane_data.get('queue_len')} 辆")

        cells = lane_data.get('cells', [])
        if sum(cells) > 0:
            print(f"   ⚠️ 该车道有车辆占用")
        else:
            print(f"   ✅ 该车道空闲")
    else:
        print(f"\n❌ 无法获取车道数据")


def example_congestion_check():
    """示例：拥堵检查"""
    print("\n" + "="*70)
    print("示例3: 路口拥堵检测")
    print("="*70)

    detector = LLMEnhancedViolationDetector(intersection_id=0)

    congestion = detector.check_traffic_congestion()

    print(f"\n🚦 路口拥堵状态:")
    print(f"   拥堵级别: {congestion['level']}")
    print(f"   是否拥堵: {'是' if congestion['is_congested'] else '否'}")
    print(f"   排队长度: {congestion['queue_length']} 辆")
    print(f"   车辆总数: {congestion['vehicle_count']} 辆")

    if congestion['is_congested']:
        print(f"\n⚠️ 建议: 路口拥堵，可能需要调整信号灯配时")


if __name__ == "__main__":
    print("\n🚦 TrafficMind - LLM增强违规检测演示")

    # 运行示例
    example_red_light_detection()
    example_lane_occupancy_check()
    example_congestion_check()

    print("\n" + "="*70)
    print("✅ 演示完成!")
    print("="*70)

    print("\n💡 应用场景:")
    print("   1. 闯红灯检测: AI视觉 + LLM信号相位双重验证")
    print("   2. 车道占用分析: 结合LLM车道数据优化检测区域")
    print("   3. 拥堵预警: 基于LLM排队长度数据触发告警")
    print("   4. 信号优化: 根据LLM流量数据调整信号配时")
    print()
