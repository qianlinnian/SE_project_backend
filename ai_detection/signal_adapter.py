"""
信号灯适配器 - 将后端传来的路口信号转换为系统使用的格式

信号代码格式说明：
- E = East (东)
- W = West (西)
- N = North (北)
- S = South (南)
- T = Through (直行)
- L = Left (左转)
- R = Right (右转)

示例：
- ETWT = East Through + West Through → 东西直行绿灯
- NTST = North Through + South Through → 南北直行绿灯
- ELWL = East Left + West Left → 东西左转绿灯
- NLSL = North Left + South Left → 南北左转绿灯

后端格式示例：
{
    "路口0": {"信号": "ETWT", "排队车辆": 4},
    "路口1": {"信号": "NTST", "排队车辆": 0},
    ...
}

系统使用格式：
{
    'north_bound': 'green',
    'south_bound': 'red',
    'west_bound': 'green',
    'east_bound': 'red'
}
"""

from typing import Dict, List, Set


class SignalAdapter:
    """信号灯适配器"""
    
    @staticmethod
    def parse_signal_code(signal_code: str) -> Set[str]:
        """
        解析信号代码，返回允许通行的方向集合
        
        信号代码格式：4位字符，前2位表示一个方向+动作，后2位表示另一个方向+动作
        
        Args:
            signal_code: 如 "ETWT" (东直行+西直行), "NTST" (北直行+南直行), "ELWL" (东左转+西左转)
            
        Returns:
            允许通行的方向集合，如 {'east_bound', 'west_bound'}
        """
        if len(signal_code) != 4:
            return set()
        
        allowed_directions = set()
        
        # 解析前两位 (第一个方向+动作)
        first_direction = signal_code[0].upper()
        first_action = signal_code[1].upper()
        
        # 解析后两位 (第二个方向+动作)
        second_direction = signal_code[2].upper()
        second_action = signal_code[3].upper()
        
        # 方向映射
        direction_map = {
            'E': 'east_bound',
            'W': 'west_bound',
            'N': 'north_bound',
            'S': 'south_bound'
        }
        
        # 添加第一个方向（如果有效）
        if first_direction in direction_map:
            allowed_directions.add(direction_map[first_direction])
        
        # 添加第二个方向（如果有效）
        if second_direction in direction_map:
            allowed_directions.add(direction_map[second_direction])
        
        return allowed_directions
    
    @staticmethod
    def convert_backend_to_system(backend_data: List[Dict]) -> Dict[str, str]:
        """
        将后端信号数据转换为系统格式
        
        Args:
            backend_data: 后端数据列表，格式如下:
                [
                    {"路口": 0, "信号": "ETWT", "排队车辆": 4},
                    {"路口": 1, "信号": "NTST", "排队车辆": 0},
                    ...
                ]
                
        Returns:
            系统格式的信号状态:
                {
                    'north_bound': 'green',
                    'south_bound': 'red',
                    'west_bound': 'green',
                    'east_bound': 'red'
                }
        """
        # 初始化所有方向为红灯
        system_states = {
            'north_bound': 'red',
            'south_bound': 'red',
            'west_bound': 'red',
            'east_bound': 'red'
        }
        
        # 收集所有允许通行的方向
        all_allowed_directions = set()
        
        # 遍历所有路口信号
        for junction_data in backend_data:
            signal_code = junction_data.get('信号', '')
            
            # 解析信号代码，获取允许通行的方向
            allowed_directions = SignalAdapter.parse_signal_code(signal_code)
            all_allowed_directions.update(allowed_directions)
        
        # 将允许通行的方向设为绿灯
        for direction in all_allowed_directions:
            if direction in system_states:
                system_states[direction] = 'green'
        
        return system_states
    
    @staticmethod
    def convert_backend_string_format(backend_text: str) -> Dict[str, str]:
        """
        从后端文本格式转换
        
        Args:
            backend_text: 如 "路口0: 信号=ETWT, 排队车辆=4\n路口1: 信号=NTST, 排队车辆=0\n..."
            
        Returns:
            系统格式的信号状态
        """
        backend_data = []
        
        lines = backend_text.strip().split('\n')
        for line in lines:
            if '信号=' in line:
                # 解析行：路口0: 信号=ETWT, 排队车辆=4
                parts = line.split(',')
                
                # 提取路口编号
                junction_part = parts[0].split(':')[0]
                junction_num = int(''.join(filter(str.isdigit, junction_part)))
                
                # 提取信号
                signal_part = parts[0].split('信号=')[1].strip()
                
                # 提取排队车辆
                queue_part = parts[1].split('排队车辆=')[1].strip()
                queue_num = int(queue_part)
                
                backend_data.append({
                    '路口': junction_num,
                    '信号': signal_part,
                    '排队车辆': queue_num
                })
        
        return SignalAdapter.convert_backend_to_system(backend_data)


# 示例用法
if __name__ == "__main__":
    # 测试数据（从您的截图）
    test_backend_data = [
        {"路口": 0, "信号": "ETWT", "排队车辆": 4},   # 东西直行
        {"路口": 1, "信号": "NTST", "排队车辆": 0},   # 南北直行
        {"路口": 2, "信号": "ETWT", "排队车辆": 1},   # 东西直行
        {"路口": 3, "信号": "ELWL", "排队车辆": 13},  # 东西左转
        {"路口": 4, "信号": "ETWT", "排队车辆": 0},
        {"路口": 5, "信号": "NTST", "排队车辆": 0},
        {"路口": 6, "信号": "NTST", "排队车辆": 3},
        {"路口": 7, "信号": "NTST", "排队车辆": 5},
        {"路口": 8, "信号": "ELWL", "排队车辆": 5},
        {"路口": 9, "信号": "NTST", "排队车辆": 0},
        {"路口": 10, "信号": "ETWT", "排队车辆": 0},
        {"路口": 11, "信号": "ETWT", "排队车辆": 1},
        {"路口": 12, "信号": "NTST", "排队车辆": 1},
        {"路口": 13, "信号": "ETWT", "排队车辆": 0},
        {"路口": 14, "信号": "ETWT", "排队车辆": 2},
        {"路口": 15, "信号": "ETWT", "排队车辆": 2},
    ]
    
    # 转换
    adapter = SignalAdapter()
    system_states = adapter.convert_backend_to_system(test_backend_data)
    
    print("🚦 后端信号转换结果：")
    for direction, state in system_states.items():
        emoji = "🟢" if state == "green" else "🔴"
        print(f"  {direction:15s}: {emoji} {state}")
    
    # 分析信号代码
    print("\n📊 信号代码分析：")
    unique_signals = set(item['信号'] for item in test_backend_data)
    for signal in sorted(unique_signals):
        allowed = SignalAdapter.parse_signal_code(signal)
        print(f"  {signal} → {', '.join(sorted(allowed))}")
    
    # 测试文本格式
    print("\n" + "="*50)
    print("📝 测试文本格式解析：")
    test_text = """路口0: 信号=ETWT, 排队车辆=4
路口1: 信号=NTST, 排队车辆=0
路口2: 信号=ETWT, 排队车辆=1
路口3: 信号=ELWL, 排队车辆=13"""
    
    result = SignalAdapter.convert_backend_string_format(test_text)
    for direction, state in result.items():
        emoji = "🟢" if state == "green" else "🔴"
        print(f"  {direction:15s}: {emoji} {state}")
