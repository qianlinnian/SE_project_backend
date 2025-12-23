"""
手动信号灯控制器 - 适用于自制测试视频

提供两种模式：
1. 键盘实时控制模式 - 运行时按键切换信号灯
2. 时间轴配置模式 - 提前配置好每个时间段的信号灯状态
"""

import json
from pathlib import Path


class ManualSignalController:
    """手动信号灯控制器"""

    def __init__(self, config_path: str = None, default_state: str = "green"):
        """
        初始化信号灯控制器

        Args:
            config_path: 信号灯时间轴配置文件（可选）
            default_state: 默认信号灯状态
        """
        self.current_states = {
            'north_bound': default_state,
            'south_bound': default_state,
            'west_bound': default_state,
            'east_bound': default_state
        }
        
        # 左转信号灯状态 (单独控制)
        self.left_turn_signals = {
            'north_bound': 'red',
            'south_bound': 'red',
            'west_bound': 'red',
            'east_bound': 'red'
        }

        # 时间轴配置 (如果有配置文件)
        self.timeline = None
        if config_path and Path(config_path).exists():
            self.load_timeline(config_path)
            print(f"✅ 加载信号灯时间轴配置: {config_path}")
        else:
            print(f"🎮 手动控制模式（使用键盘控制）")

    def load_timeline(self, config_path: str):
        """
        加载信号灯时间轴配置

        配置文件格式示例 (signal_timeline.json):
        {
            "timeline": [
                {"start": 0, "end": 10, "states": {"north_bound": "green", "south_bound": "green", "west_bound": "red", "east_bound": "red"}},
                {"start": 10, "end": 20, "states": {"north_bound": "red", "south_bound": "red", "west_bound": "green", "east_bound": "green"}},
                {"start": 20, "end": 30, "states": {"north_bound": "green", "south_bound": "green", "west_bound": "red", "east_bound": "red"}}
            ]
        }
        """
        with open(config_path, 'r', encoding='utf-8') as f:
            config = json.load(f)
            self.timeline = config.get('timeline', [])

    def get_signal_states(self, video_time: float = None):
        """
        获取当前信号灯状态

        Args:
            video_time: 视频时间（秒），如果使用时间轴配置

        Returns:
            dict: {'north_bound': 'red', ...}
        """
        # 如果有时间轴配置，根据时间返回
        if self.timeline and video_time is not None:
            for segment in self.timeline:
                if segment['start'] <= video_time < segment['end']:
                    return segment['states']

        # 否则返回当前手动设置的状态
        return self.current_states.copy()
    
    def get_left_turn_signals(self, video_time: float = None):
        """
        获取当前左转信号灯状态

        Args:
            video_time: 视频时间（秒），如果使用时间轴配置

        Returns:
            dict: {'north_bound': 'red', ...}
        """
        # 如果有时间轴配置，根据时间返回
        if self.timeline and video_time is not None:
            for segment in self.timeline:
                if segment['start'] <= video_time < segment['end']:
                    # 如果配置中有left_turn_signals字段，使用它
                    if 'left_turn_signals' in segment:
                        return segment['left_turn_signals']

        # 否则返回当前手动设置的左转信号状态
        return self.left_turn_signals.copy()

    def set_all_red(self):
        """所有方向设为红灯"""
        for direction in self.current_states:
            self.current_states[direction] = 'red'
        print("🚦 所有方向 -> 🔴 红灯")

    def set_all_green(self):
        """所有方向设为绿灯"""
        for direction in self.current_states:
            self.current_states[direction] = 'green'
        print("🚦 所有方向 -> 🟢 绿灯")

    def set_north_south_green(self):
        """南北方向绿灯，东西方向红灯"""
        self.current_states['north_bound'] = 'green'
        self.current_states['south_bound'] = 'green'
        self.current_states['west_bound'] = 'red'
        self.current_states['east_bound'] = 'red'
        print("🚦 南北 -> 🟢 绿灯 | 东西 -> 🔴 红灯")

    def set_west_east_green(self):
        """东西方向绿灯，南北方向红灯"""
        self.current_states['north_bound'] = 'red'
        self.current_states['south_bound'] = 'red'
        self.current_states['west_bound'] = 'green'
        self.current_states['east_bound'] = 'green'
        print("🚦 南北 -> 🔴 红灯 | 东西 -> 🟢 绿灯")
    
    def set_all_left_turn_red(self):
        """所有方向左转灯设为红灯"""
        for direction in self.left_turn_signals:
            self.left_turn_signals[direction] = 'red'
        print("🚦 所有方向左转灯 -> 🔴 红灯")
    
    def set_all_left_turn_green(self):
        """所有方向左转灯设为绿灯"""
        for direction in self.left_turn_signals:
            self.left_turn_signals[direction] = 'green'
        print("🚦 所有方向左转灯 -> 🟢 绿灯")
    
    def toggle_left_turn(self, direction: str):
        """切换指定方向的左转灯（红<->绿）"""
        if direction in self.left_turn_signals:
            current = self.left_turn_signals[direction]
            new_state = 'green' if current == 'red' else 'red'
            self.left_turn_signals[direction] = new_state
            emoji = "🟢" if new_state == 'green' else "🔴"
            print(f"🚦 {direction} 左转灯 -> {emoji} {new_state.upper()}")

    def set_direction(self, direction: str, state: str):
        """
        设置指定方向的信号灯

        Args:
            direction: 方向 (north_bound, south_bound, west_bound, east_bound)
            state: 状态 ('red', 'green', 'yellow')
        """
        if direction in self.current_states:
            self.current_states[direction] = state
            print(f"🚦 {direction} -> {state}")

    def handle_keyboard(self, key: int):
        """
        处理键盘输入（用于实时控制）

        按键映射：
        - '1': 所有红灯
        - '2': 所有绿灯
        - '3': 南北绿灯
        - '4': 东西绿灯
        - 'n': 北向红灯
        - 's': 南向红灯
        - 'w': 西向红灯
        - 'e': 东向红灯

        Args:
            key: cv2.waitKey() 返回的按键值

        Returns:
            bool: 是否处理了按键
        """
        if key == ord('1'):
            self.set_all_red()
            return True
        elif key == ord('2'):
            self.set_all_green()
            return True
        elif key == ord('3'):
            self.set_north_south_green()
            return True
        elif key == ord('4'):
            self.set_west_east_green()
            return True
        elif key == ord('n'):
            self.toggle_direction('north_bound')
            return True
        elif key == ord('s'):
            self.toggle_direction('south_bound')
            return True
        elif key == ord('w'):
            self.toggle_direction('west_bound')
            return True
        elif key == ord('e'):
            self.toggle_direction('east_bound')
            return True
        # 左转灯控制
        elif key == ord('5'):
            self.set_all_left_turn_red()
            return True
        elif key == ord('6'):
            self.set_all_left_turn_green()
            return True
        elif key == ord('7'):
            self.toggle_left_turn('north_bound')
            return True
        elif key == ord('8'):
            self.toggle_left_turn('south_bound')
            return True
        elif key == ord('9'):
            self.toggle_left_turn('west_bound')
            return True
        elif key == ord('0'):
            self.toggle_left_turn('east_bound')
            return True

        return False

    def toggle_direction(self, direction: str):
        """切换指定方向的信号灯（红<->绿）"""
        current = self.current_states[direction]
        new_state = 'green' if current == 'red' else 'red'
        self.set_direction(direction, new_state)

    def print_controls(self):
        """打印控制说明"""
        print("\n" + "=" * 60)
        print("🎮 信号灯控制键位")
        print("=" * 60)
        print("  直行信号灯:")
        print("    [1] - 所有方向 红灯")
        print("    [2] - 所有方向 绿灯")
        print("    [3] - 南北 绿灯 | 东西 红灯")
        print("    [4] - 南北 红灯 | 东西 绿灯")
        print("    [N] - 切换北向信号灯")
        print("    [S] - 切换南向信号灯")
        print("    [W] - 切换西向信号灯")
        print("    [E] - 切换东向信号灯")
        print("")
        print("  左转信号灯:")
        print("    [5] - 所有方向左转灯 红灯")
        print("    [6] - 所有方向左转灯 绿灯")
        print("    [7] - 切换北向左转灯")
        print("    [8] - 切换南向左转灯")
        print("    [9] - 切换西向左转灯")
        print("    [0] - 切换东向左转灯")
        print("")
        print("  [Q] - 退出")
        print("=" * 60 + "\n")


def create_timeline_template(output_path: str = "./signal_timeline.json"):
    """
    创建信号灯时间轴配置模板

    Args:
        output_path: 输出文件路径
    """
    template = {
        "description": "信号灯时间轴配置 - 为你的测试视频配置信号灯状态",
        "video_name": "your_video.mp4",
        "timeline": [
            {
                "start": 0,
                "end": 10,
                "description": "0-10秒：南北绿灯",
                "states": {
                    "north_bound": "green",
                    "south_bound": "green",
                    "west_bound": "red",
                    "east_bound": "red"
                }
            },
            {
                "start": 10,
                "end": 20,
                "description": "10-20秒：东西绿灯",
                "states": {
                    "north_bound": "red",
                    "south_bound": "red",
                    "west_bound": "green",
                    "east_bound": "green"
                }
            },
            {
                "start": 20,
                "end": 30,
                "description": "20-30秒：南北绿灯",
                "states": {
                    "north_bound": "green",
                    "south_bound": "green",
                    "west_bound": "red",
                    "east_bound": "red"
                }
            }
        ]
    }

    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(template, f, indent=2, ensure_ascii=False)

    print(f"✅ 信号灯时间轴模板已创建: {output_path}")
    print("💡 请根据你的视频内容修改时间段和信号灯状态")


if __name__ == "__main__":
    # 测试：创建配置模板
    print("创建信号灯时间轴配置模板...")
    create_timeline_template()

    # 测试：手动控制模式
    print("\n测试手动控制模式:")
    controller = ManualSignalController()
    controller.print_controls()

    print("\n测试信号灯控制:")
    controller.set_north_south_green()
    print(controller.get_signal_states())

    controller.set_west_east_green()
    print(controller.get_signal_states())

    controller.set_all_red()
    print(controller.get_signal_states())
