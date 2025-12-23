"""
违规检测模块 - TrafficMind 交通智脑

功能：
1. 闯红灯检测 (Red Light Running Detection)
2. 逆行检测 (Wrong-Way Driving Detection)
3. 跨实线变道检测 (Lane Change Across Solid Line Detection)
4. 自动上报违规到后端 API
"""

import cv2
import numpy as np
import json
from datetime import datetime
from collections import defaultdict
from pathlib import Path


class ViolationDetector:
    """违规检测器（带后端API集成）"""

    def __init__(self, rois_path: str, screenshot_dir: str = "./violations",
                 intersection_id: int = 1, enable_api: bool = True):
        """
        初始化违规检测器

        Args:
            rois_path: ROI配置文件路径 (rois.json)
            screenshot_dir: 违规截图保存目录
            intersection_id: 路口ID（用于API上报）
            enable_api: 是否启用API上报
        """
        # 加载ROI数据
        with open(rois_path, 'r', encoding='utf-8') as f:
            self.rois = json.load(f)

        # 创建截图保存目录
        self.screenshot_dir = Path(screenshot_dir)
        self.screenshot_dir.mkdir(parents=True, exist_ok=True)

        # 信号灯状态 (需要外部设置)
        self.traffic_lights = {
            'north_bound': 'red',  # green, red, yellow - 改为红灯测试闯红灯
            'south_bound': 'red',  # 改为红灯测试闯红灯
            'west_bound': 'green',
            'east_bound': 'green'  # 改为绿灯
        }
        
        # 左转信号灯状态 (单独控制左转)
        # 'green' - 允许左转, 'red' - 禁止左转
        self.left_turn_signals = {
            'north_bound': 'red',
            'south_bound': 'red',
            'west_bound': 'red',
            'east_bound': 'red'
        }

        # 车辆轨迹记录 {track_id: [(x, y, timestamp), ...]}
        self.vehicle_trajectories = defaultdict(list)
        
        # 车辆停止线穿越状态 {track_id: {direction: {'before': bool, 'crossed': bool}}}
        # before: 车辆是否曾在停止线前方
        # crossed: 车辆是否已穿越停止线
        self.vehicle_stop_line_status = defaultdict(lambda: defaultdict(lambda: {'before': False, 'crossed': False}))
        
        # 车辆实线穿越记录 {track_id: {solid_line_name: {'side': int, 'last_pos': tuple}}}
        # 用于检测跨实线变道
        # side: -1(左侧), 1(右侧), 0(在线上)
        self.vehicle_solid_line_status = defaultdict(dict)
        
        # 车辆待转区状态跟踪 {track_id: {direction: {'outside': bool, 'inside': bool, 'enter_time': float}}}
        # outside: 车辆是否曾在待转区外
        # inside: 车辆当前是否在待转区内
        # enter_time: 进入待转区的时间戳
        self.vehicle_waiting_area_status = defaultdict(lambda: defaultdict(lambda: {'outside': False, 'inside': False, 'enter_time': None}))

        # 违规记录
        self.violations = []
        
        # 违规去重记录 {(track_id, violation_type): last_timestamp}
        # 用于防止同一车辆短时间内重复记录相同类型的违规
        self.violation_cooldown = {}
        self.cooldown_period = 1000.0  # 冷却时间：1000ms

        # ========== API 集成配置 ==========
        self.intersection_id = intersection_id
        self.enable_api = enable_api
        self.api_client = None

        if enable_api:
            try:
                from backend_api_client import BackendAPIClient
                self.api_client = BackendAPIClient()
                # 测试连接
                if self.api_client.health_check():
                    print("[API] ✅ 后端连接成功，违规将自动上报到 http://localhost:8081")
                else:
                    print("[API] ⚠️ 后端连接失败，将只记录本地违规")
                    self.enable_api = False
            except ImportError:
                print("[API] ⚠️ 未找到 backend_api_client 模块，将只记录本地违规")
                self.enable_api = False
            except Exception as e:
                print(f"[API] ⚠️ API客户端初始化失败: {e}")
                self.enable_api = False

    # ========== API 映射方法 ==========
    
    def _map_direction_to_api(self, direction: str) -> str:
        """将内部方向格式转换为后端API格式"""
        mapping = {
            'north_bound': 'NORTH',
            'south_bound': 'SOUTH',
            'west_bound': 'WEST',
            'east_bound': 'EAST'
        }
        return mapping.get(direction, 'NORTH')

    def _map_violation_type_to_api(self, violation_type: str) -> str:
        """将内部违规类型转换为后端API格式"""
        mapping = {
            'red_light_running': 'RED_LIGHT',
            'wrong_way_driving': 'WRONG_WAY',
            'lane_change_across_solid_line': 'CROSS_SOLID_LINE',
            'waiting_area_red_entry': 'ILLEGAL_TURN',
            'waiting_area_illegal_exit': 'ILLEGAL_TURN'
        }
        return mapping.get(violation_type, 'OTHER')

    def _report_to_backend(self, violation_record: dict, frame=None):
        """
        上报违规到后端API
        
        Args:
            violation_record: 本地违规记录字典
            frame: 当前帧（可选）
        """
        if not self.enable_api or self.api_client is None:
            return
        
        try:
            # 获取截图路径
            screenshot_path = violation_record.get('screenshot', '')
            
            # 上传图片（当前返回本地路径）
            image_url = self.api_client.upload_image(screenshot_path) if screenshot_path else 'file:///no_image.jpg'
            
            # 转换时间戳
            timestamp_str = violation_record.get('timestamp', datetime.now().isoformat())
            
            # 获取方向
            direction = violation_record.get('direction', 'north_bound')
            
            # 生成临时车牌号（基于 track_id）
            track_id = violation_record.get('track_id', 0)
            plate_number = f"未识别_{track_id}"
            
            # 准备 API 数据
            api_data = {
                'intersectionId': self.intersection_id,
                'direction': self._map_direction_to_api(direction),
                'turnType': 'STRAIGHT',  # 默认直行
                'plateNumber': plate_number,
                'violationType': self._map_violation_type_to_api(violation_record.get('type', '')),
                'imageUrl': image_url,
                'aiConfidence': 0.95,
                'occurredAt': timestamp_str
            }
            
            # 调用 API 上报
            violation_id = self.api_client.report_violation(api_data)
            
            if violation_id:
                violation_record['backend_id'] = violation_id
                print(f"[API] ✅ 上报成功! 后端ID: {violation_id}")
            else:
                print(f"[API] ❌ 上报失败")
                
        except Exception as e:
            print(f"[API] ❌ 上报异常: {type(e).__name__}: {e}")

    def update_signal_state(self, direction: str, state: str, force_print=False):
        """
        更新信号灯状态

        Args:
            direction: 方向 (north_bound, south_bound, west_bound, east_bound)
            state: 状态 ('green', 'red', 'yellow')
            force_print: 是否强制打印状态变化
        """
        if direction in self.traffic_lights:
            old_state = self.traffic_lights[direction]
            self.traffic_lights[direction] = state
            # 只在状态真正变化且强制打印时输出
            if force_print and old_state != state:
                print(f"[信号切换] {direction}: {old_state} -> {state}")
    
    def update_left_turn_signal(self, direction: str, state: str, force_print=False):
        """
        更新左转信号灯状态
        
        Args:
            direction: 方向 (north_bound, south_bound, west_bound, east_bound)
            state: 状态 ('green' - 允许左转, 'red' - 禁止左转)
            force_print: 是否强制打印状态变化
        """
        if direction in self.left_turn_signals:
            old_state = self.left_turn_signals[direction]
            self.left_turn_signals[direction] = state
            if force_print and old_state != state:
                print(f"[左转信号切换] {direction}: {old_state} -> {state}")

    def _should_record_violation(self, track_id: int, violation_type: str, timestamp: float, verbose=False) -> bool:
        """
        检查是否应该记录违规（去重逻辑）
        
        同一辆车在冷却时间内，相同类型的违规只记录一次
        不同类型的违规分别记录
        
        Args:
            track_id: 车辆ID
            violation_type: 违规类型 ('red_light_running' 或 'wrong_way_driving')
            timestamp: 当前时间戳
            verbose: 是否打印详细调试信息
            
        Returns:
            bool: True表示应该记录，False表示在冷却期内，跳过
        """
        key = (track_id, violation_type)
        
        # 检查是否在冷却期内
        if key in self.violation_cooldown:
            last_time = self.violation_cooldown[key]
            time_diff = timestamp - last_time
            if time_diff < self.cooldown_period:
                # 还在冷却期内，不记录（仅在verbose模式打印）
                if verbose:
                    print(f"[去重] Track {track_id} {violation_type} 在冷却期内 (距上次{time_diff:.1f}秒)")
                return False
        
        # 记录本次违规时间
        self.violation_cooldown[key] = timestamp
        print(f"[记录] Track {track_id} {violation_type} 已记录")
        return True

    def is_point_in_polygon(self, point, polygon, tolerance=0):
        """
        判断点是否在多边形内 (使用 OpenCV 的 pointPolygonTest)

        Args:
            point: (x, y) 坐标
            polygon: 多边形顶点列表 [[x1,y1], [x2,y2], ...]
            tolerance: 容差（像素），默认为0表示必须严格在多边形内

        Returns:
            bool: True 表示在多边形内
        """
        poly = np.array(polygon, dtype=np.int32)
        result = cv2.pointPolygonTest(poly, point, False)
        # result >= 0: 在内部或边界上
        # result < 0: 在外部
        return result >= -tolerance

    def detect_red_light_violation(self, track_id: int, bbox, frame, timestamp):
        """
        检测闯红灯违规
        
        正确的逻辑：
        1. 追踪车辆相对于停止线的位置（前方/内部）
        2. 检测车辆是否在红灯期间从停止线前方穿越到停止线内
        3. 只有在红灯时穿越才算闯红灯

        Args:
            track_id: 车辆追踪ID
            bbox: 边界框 (x1, y1, x2, y2)
            frame: 当前帧图像
            timestamp: 时间戳

        Returns:
            dict or None: 违规记录字典，如果没有违规则返回 None
        """
        # 计算车辆中心点 (bbox的底部中心点更准确)
        x1, y1, x2, y2 = bbox
        vehicle_center = (int((x1 + x2) / 2), int(y2))  # 底部中心

        # 遍历所有方向的停止线
        for direction, data in self.rois.items():
            # 跳过 solid_lines 字段
            if direction == 'solid_lines':
                continue
                
            # 检查车辆是否在停止线内
            is_in_stop_line = False
            for stop_line_poly in data['stop_line']:
                if self.is_point_in_polygon(vehicle_center, stop_line_poly):
                    is_in_stop_line = True
                    break
            
            # 获取该车辆在这个方向的状态
            status = self.vehicle_stop_line_status[track_id][direction]
            
            if is_in_stop_line:
                # 车辆现在在停止线内
                if status['before'] and not status['crossed']:
                    # 车辆之前在停止线前方，现在穿越进来了
                    # 检查当前信号灯状态
                    if self.traffic_lights.get(direction) == 'red':
                        # 红灯时穿越 = 闯红灯！
                        # 检查去重
                        if not self._should_record_violation(track_id, 'red_light_running', timestamp):
                            status['crossed'] = True  # 标记已穿越，避免重复检测
                            return None  # 在冷却期内，跳过
                        
                        violation_id = f"RED_{direction}_{track_id}_{int(timestamp)}"
                        screenshot_path = self.save_violation_screenshot(
                            frame, bbox, violation_id, "red_light"
                        )

                        violation_record = {
                            'id': violation_id,
                            'type': 'red_light_running',
                            'track_id': track_id,
                            'direction': direction,
                            'timestamp': datetime.fromtimestamp(timestamp).isoformat(),
                            'location': vehicle_center,
                            'bbox': bbox,
                            'screenshot': str(screenshot_path)
                        }

                        self.violations.append(violation_record)
                        print(f"⚠️ [闯红灯] Track {track_id} @ {direction}")
                        
                        # 上报到后端 API
                        self._report_to_backend(violation_record, frame)
                        
                        status['crossed'] = True  # 标记已穿越
                        return violation_record
                    else:
                        # 绿灯时穿越，正常通行
                        status['crossed'] = True
                else:
                    # 车辆持续在停止线内，不做处理
                    pass
            else:
                # 车辆不在停止线内
                # 检查车辆是否在停止线前方（通过检查in车道）
                is_before_stop_line = False
                for lane_poly in data['lanes'].get('in', []):
                    if self.is_point_in_polygon(vehicle_center, lane_poly):
                        is_before_stop_line = True
                        break
                
                if is_before_stop_line:
                    # 车辆在停止线前方的进入车道
                    status['before'] = True
                    status['crossed'] = False  # 重置穿越状态
                else:
                    # 车辆既不在停止线也不在进入车道，可能已经离开路口
                    # 重置状态
                    if status['crossed']:
                        status['before'] = False
                        status['crossed'] = False

        return None

    def detect_wrong_way(self, track_id: int, current_pos, timestamp):
        """
        检测逆行违规

        逻辑：
        1. 获取车辆的历史轨迹
        2. 判断车辆当前位于哪条车道(in/out)
        3. 计算移动方向，判断是否逆行

        Args:
            track_id: 车辆追踪ID
            current_pos: 当前位置 (x, y)
            timestamp: 时间戳

        Returns:
            dict or None: 违规记录，如果没有违规则返回 None
        """
        # 记录轨迹
        self.vehicle_trajectories[track_id].append((current_pos[0], current_pos[1], timestamp))

        # 至少需要3个点才能判断方向
        trajectory = self.vehicle_trajectories[track_id]
        if len(trajectory) < 3:
            return None

        # 只保留最近2秒的轨迹
        trajectory = [p for p in trajectory if timestamp - p[2] <= 2.0]
        self.vehicle_trajectories[track_id] = trajectory

        if len(trajectory) < 3:
            return None
        
        # 先判断车辆的主要移动方向
        start_point = trajectory[0][:2]
        end_point = trajectory[-1][:2]
        dx = end_point[0] - start_point[0]
        dy = end_point[1] - start_point[1]
        
        # 判断是南北向还是东西向（基于移动的主方向）
        is_ns_direction = abs(dy) > abs(dx)  # True: 南北向, False: 东西向
        
        # 根据主方向，只检查对应的方向车道
        if is_ns_direction:
            # 南北向移动，只检查 north_bound 和 south_bound
            directions_to_check = ['north_bound', 'south_bound']
        else:
            # 东西向移动，只检查 west_bound 和 east_bound
            directions_to_check = ['west_bound', 'east_bound']

        # 检查车辆在哪条车道中（只检查相关方向）
        for direction in directions_to_check:
            data = self.rois[direction]
            
            # 检查in车道（驶入路口）
            for lane_idx, lane_poly in enumerate(data['lanes'].get('in', [])):
                if self.is_point_in_polygon(current_pos, lane_poly):
                    # 车辆在 in 车道中，判断是否逆行
                    is_wrong = self._is_moving_wrong_direction_in(
                        direction, start_point, end_point
                    )

                    if is_wrong:
                        # 检查是否应该记录（去重）
                        if not self._should_record_violation(track_id, 'wrong_way_driving', timestamp):
                            return None  # 在冷却期内，跳过
                        
                        # 只在要记录违规时打印详细信息
                        print(f"[逆行检测] Track {track_id} @ {direction} in-lane {lane_idx}")
                        print(f"  当前位置: {current_pos}, 起点: {start_point}, 终点: {end_point}")
                        print(f"  移动向量: dy={dy:.1f}, dx={dx:.1f}")
                        print(f"  轨迹点数: {len(trajectory)}, 时间跨度: {trajectory[-1][2] - trajectory[0][2]:.1f}秒")
                        
                        violation_id = f"WRONG_{direction}_{track_id}_{int(timestamp)}"
                        violation_record = {
                            'id': violation_id,
                            'type': 'wrong_way_driving',
                            'track_id': track_id,
                            'direction': direction,
                            'lane_type': 'in',
                            'lane_index': lane_idx,
                            'timestamp': datetime.fromtimestamp(timestamp).isoformat(),
                            'trajectory': trajectory
                        }
                        self.violations.append(violation_record)
                        print(f"⚠️ [逆行] Track {track_id} @ {direction} in-lane {lane_idx} (dy={dy:.1f}, dx={dx:.1f})")
                        
                        # 上报到后端 API
                        self._report_to_backend(violation_record)
                        
                        return violation_record
            
            # 检查out车道（驶出路口）
            for lane_idx, lane_poly in enumerate(data['lanes'].get('out', [])):
                if self.is_point_in_polygon(current_pos, lane_poly):
                    # 车辆在 out 车道中
                    # 根据方向判断是否逆行（out车道的正确方向）
                    is_wrong = self._is_moving_wrong_direction_out(
                        direction, start_point, end_point
                    )

                    if is_wrong:
                        # 检查是否应该记录（去重）
                        if not self._should_record_violation(track_id, 'wrong_way_driving', timestamp):
                            return None  # 在冷却期内，跳过
                        
                        violation_id = f"WRONG_{direction}_{track_id}_{int(timestamp)}"
                        violation_record = {
                            'id': violation_id,
                            'type': 'wrong_way_driving',
                            'track_id': track_id,
                            'direction': direction,
                            'lane_type': 'out',
                            'lane_index': lane_idx,
                            'timestamp': datetime.fromtimestamp(timestamp).isoformat(),
                            'trajectory': trajectory
                        }
                        self.violations.append(violation_record)
                        print(f"⚠️ [逆行] Track {track_id} @ {direction} out-lane {lane_idx} (dy={dy:.1f}, dx={dx:.1f})")
                        
                        # 上报到后端 API
                        self._report_to_backend(violation_record)
                        
                        return violation_record

        return None

    def _is_moving_wrong_direction_in(self, direction: str, start_pos, end_pos):
        """
        判断车辆在in车道中的移动方向是否正确
        
        in车道：车辆应该驶向路口中心
        - north_bound in: 从上往下 (y增大) - 正确
        - south_bound in: 从下往上 (y减小) - 正确  
        - west_bound in: 从左往右 (x增大) - 正确
        - east_bound in: 从右往左 (x减小) - 正确

        Args:
            direction: 方向
            start_pos: 起始位置 (x, y)
            end_pos: 结束位置 (x, y)

        Returns:
            bool: True表示逆行
        """
        dx = end_pos[0] - start_pos[0]
        dy = end_pos[1] - start_pos[1]
        threshold = 10

        if direction == 'north_bound':
            # 正确：从上往下 (y增大)，逆行：从下往上 (y减小)
            return dy < -threshold
        elif direction == 'south_bound':
            # 正确：从下往上 (y减小)，逆行：从上往下 (y增大)
            return dy > threshold
        elif direction == 'west_bound':
            # 正确：从左往右 (x增大)，逆行：从右往左 (x减小)
            return dx < -threshold
        elif direction == 'east_bound':
            # 正确：从右往左 (x减小)，逆行：从左往右 (x增大)
            return dx > threshold

        return False

    def _is_moving_wrong_direction_out(self, direction: str, start_pos, end_pos):
        """
        判断车辆在out车道中的移动方向是否正确
        
        out车道：车辆应该背离路口中心
        - north_bound out: 从下往上 (y减小) - 正确
        - south_bound out: 从上往下 (y增大) - 正确
        - west_bound out: 从右往左 (x减小) - 正确
        - east_bound out: 从左往右 (x增大) - 正确

        Args:
            direction: 方向
            start_pos: 起始位置 (x, y)
            end_pos: 结束位置 (x, y)

        Returns:
            bool: True表示逆行
        """
        dx = end_pos[0] - start_pos[0]
        dy = end_pos[1] - start_pos[1]
        threshold = 10

        if direction == 'north_bound':
            # 正确：从下往上 (y减小)，逆行：从上往下 (y增大)
            return dy > threshold
        elif direction == 'south_bound':
            # 正确：从上往下 (y增大)，逆行：从下往上 (y减小)
            return dy < -threshold
        elif direction == 'west_bound':
            # 正确：从右往左 (x减小)，逆行：从左往右 (x增大)
            return dx > threshold
        elif direction == 'east_bound':
            # 正确：从左往右 (x增大)，逆行：从右往左 (x减小)
            return dx < -threshold

        return False

    def detect_lane_change_violation(self, track_id: int, bbox, frame, timestamp):
        """
        检测跨实线变道违规 - 基于距离的简化算法
        
        逻辑：
        1. 先判断车辆是否在某个车道区域内（IN或OUT）
        2. 如果不在车道内（路口中心），不检测跨实线
        3. 如果在车道内，检测该方向的实线
        4. 计算车辆中心点到实线的距离
        5. 判断车辆是否从实线一侧穿越到另一侧
        6. 如果穿越 → 记录违规
        
        Args:
            track_id: 车辆追踪ID
            bbox: 边界框 (x1, y1, x2, y2)
            frame: 当前帧图像
            timestamp: 时间戳
            
        Returns:
            dict or None: 违规记录字典，如果没有违规则返回 None
        """
        # 计算车辆中心点
        x1, y1, x2, y2 = bbox
        vehicle_center = (int((x1 + x2) / 2), int(y2))
        
        # 获取轨迹数据
        trajectory = self.vehicle_trajectories.get(track_id, [])
        if len(trajectory) < 5:
            return None
        
        # 计算移动方向，确定要检查的方向
        start_point = trajectory[0][:2]
        end_point = trajectory[-1][:2]
        dx = end_point[0] - start_point[0]
        dy = end_point[1] - start_point[1]
        
        if abs(dy) > abs(dx):
            directions_to_check = ['north_bound', 'south_bound']
        else:
            directions_to_check = ['west_bound', 'east_bound']
        
        # 1. 先判断车辆是否在某个车道区域内
        current_direction = None
        current_lane_type = None  # 'in' 或 'out'
        
        for direction in directions_to_check:
            data = self.rois[direction]
            # 检查IN车道
            for lane_poly in data['lanes'].get('in', []):
                if self.is_point_in_polygon(vehicle_center, lane_poly):
                    current_direction = direction
                    current_lane_type = 'in'
                    break
            
            # 如果没找到，检查OUT车道
            if current_direction is None:
                for lane_poly in data['lanes'].get('out', []):
                    if self.is_point_in_polygon(vehicle_center, lane_poly):
                        current_direction = direction
                        current_lane_type = 'out'
                        break
            
            if current_direction:
                break
        
        # 如果车辆不在任何车道内（路口中心），不检测跨实线
        if current_direction is None:
            return None
        
        # 2. 只检查当前方向的实线
        solid_lines = self.rois.get('solid_lines', [])
        for solid_line in solid_lines:
            # 只检查当前车辆所在方向的实线
            if solid_line['direction'] != current_direction:
                continue
            
            line_name = solid_line['name']
            coords = solid_line['coordinates']
            if len(coords) != 2:
                continue
            
            # 计算点到线段的距离和侧面
            dist, side = self._point_to_line_distance_and_side(vehicle_center, coords[0], coords[1])
            
            # 距离阈值：15像素（靠近实线）
            if dist < 15:
                # 获取历史状态
                if line_name not in self.vehicle_solid_line_status[track_id]:
                    # 第一次接近这条实线，记录当前侧面
                    self.vehicle_solid_line_status[track_id][line_name] = {
                        'side': side,
                        'last_pos': vehicle_center
                    }
                else:
                    prev_status = self.vehicle_solid_line_status[track_id][line_name]
                    prev_side = prev_status['side']
                    
                    # 检测穿越：从一侧到另一侧（side符号改变）
                    if prev_side != 0 and side != 0 and prev_side != side:
                        # 穿越实线！
                        
                        # 检查去重
                        if not self._should_record_violation(track_id, 'lane_change_across_solid_line', timestamp):
                            # 更新状态
                            self.vehicle_solid_line_status[track_id][line_name]['side'] = side
                            self.vehicle_solid_line_status[track_id][line_name]['last_pos'] = vehicle_center
                            return None
                        
                        violation_id = f"LANE_{line_name}_{track_id}_{int(timestamp)}"
                        screenshot_path = self.save_violation_screenshot(
                            frame, bbox, violation_id, "lane_change"
                        )
                        
                        violation_record = {
                            'id': violation_id,
                            'type': 'lane_change_across_solid_line',
                            'track_id': track_id,
                            'solid_line': line_name,
                            'direction': solid_line['direction'],
                            'timestamp': datetime.fromtimestamp(timestamp).isoformat(),
                            'screenshot': str(screenshot_path),
                            'trajectory': trajectory
                        }
                        self.violations.append(violation_record)
                        print(f"⚠️ [跨实线变道] Track {track_id} 穿越 {line_name}")
                        
                        # 上报到后端 API
                        self._report_to_backend(violation_record, frame)
                        
                        # 更新状态
                        self.vehicle_solid_line_status[track_id][line_name]['side'] = side
                        self.vehicle_solid_line_status[track_id][line_name]['last_pos'] = vehicle_center
                        return violation_record
                    
                    # 更新位置
                    self.vehicle_solid_line_status[track_id][line_name]['side'] = side
                    self.vehicle_solid_line_status[track_id][line_name]['last_pos'] = vehicle_center
        
        return None
    
    def _point_to_line_distance_and_side(self, point, line_p1, line_p2):
        """
        计算点到线段的距离，并判断点在线段的哪一侧
        
        Args:
            point: (x, y) 点坐标
            line_p1: 线段起点 (x1, y1)
            line_p2: 线段终点 (x2, y2)
        
        Returns:
            (distance, side)
            distance: 点到线段的垂直距离
            side: -1(左侧), 1(右侧), 0(在线上)
        """
        x0, y0 = point
        x1, y1 = line_p1
        x2, y2 = line_p2
        
        # 使用叉积判断侧面
        # cross = (x2-x1)*(y0-y1) - (y2-y1)*(x0-x1)
        cross = (x2 - x1) * (y0 - y1) - (y2 - y1) * (x0 - x1)
        
        # 计算点到线段的距离
        line_len_sq = (x2 - x1)**2 + (y2 - y1)**2
        if line_len_sq == 0:
            # 线段退化为点
            dist = np.sqrt((x0 - x1)**2 + (y0 - y1)**2)
        else:
            # 点到直线的垂直距离
            dist = abs(cross) / np.sqrt(line_len_sq)
        
        # 判断侧面
        if abs(cross) < 1e-6:
            side = 0  # 在线上
        elif cross > 0:
            side = 1  # 右侧
        else:
            side = -1  # 左侧
        
        return dist, side

    def save_violation_screenshot(self, frame, bbox, violation_id: str, vtype: str):
        """
        保存违规截图（只保存车辆和周边区域）

        Args:
            frame: 当前帧
            bbox: 车辆边界框
            violation_id: 违规ID
            vtype: 违规类型

        Returns:
            Path: 截图保存路径
        """
        x1, y1, x2, y2 = bbox
        frame_h, frame_w = frame.shape[:2]
        
        # 计算车辆中心和尺寸
        car_w = x2 - x1
        car_h = y2 - y1
        
        # 扩展边界：车辆周围增加50%的空间（可调整）
        expand_ratio = 0.5
        expand_w = int(car_w * expand_ratio)
        expand_h = int(car_h * expand_ratio)
        
        # 计算裁剪区域，确保不超出画面边界
        crop_x1 = max(0, int(x1 - expand_w))
        crop_y1 = max(0, int(y1 - expand_h))
        crop_x2 = min(frame_w, int(x2 + expand_w))
        crop_y2 = min(frame_h, int(y2 + expand_h))
        
        # 裁剪车辆周边区域
        cropped_frame = frame[crop_y1:crop_y2, crop_x1:crop_x2].copy()
        
        # 在裁剪后的图像上绘制边界框（需要调整坐标）
        box_x1 = int(x1 - crop_x1)
        box_y1 = int(y1 - crop_y1)
        box_x2 = int(x2 - crop_x1)
        box_y2 = int(y2 - crop_y1)
        
        cv2.rectangle(cropped_frame, (box_x1, box_y1), (box_x2, box_y2), (0, 0, 255), 3)
        cv2.putText(cropped_frame, f"{vtype.upper()}", (box_x1, max(10, box_y1-10)),
                    cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)

        # 保存截图
        filename = f"{violation_id}.jpg"
        filepath = self.screenshot_dir / filename
        cv2.imwrite(str(filepath), cropped_frame)

        return filepath

    def process_frame(self, frame, detections, timestamp):
        """
        处理每一帧的检测结果

        Args:
            frame: 当前帧图像
            detections: 检测结果列表 [(track_id, bbox), ...]
                       bbox = (x1, y1, x2, y2)
            timestamp: 时间戳

        Returns:
            list: 本帧检测到的违规列表
        """
        frame_violations = []

        for track_id, bbox in detections:
            # 1. 闯红灯检测
            red_light_violation = self.detect_red_light_violation(
                track_id, bbox, frame, timestamp
            )
            if red_light_violation:
                frame_violations.append(red_light_violation)

            # 2. 逆行检测
            x1, y1, x2, y2 = bbox
            # 使用底部中心点（车轮位置）而不是边界框中心
            bottom_center_pos = (int((x1 + x2) / 2), int(y2))

            wrong_way_violation = self.detect_wrong_way(
                track_id, bottom_center_pos, timestamp
            )
            if wrong_way_violation:
                frame_violations.append(wrong_way_violation)
            
            # 3. 跨实线变道检测
            lane_change_violation = self.detect_lane_change_violation(
                track_id, bbox, frame, timestamp
            )
            if lane_change_violation:
                frame_violations.append(lane_change_violation)
            
            # 4. 左转待转区违规检测
            waiting_area_violation = self.detect_waiting_area_violation(
                track_id, bbox, frame, timestamp
            )
            if waiting_area_violation:
                frame_violations.append(waiting_area_violation)

        return frame_violations

    def detect_waiting_area_violation(self, track_id: int, bbox, frame, timestamp):
        """
        检测左转待转区违规
        
        违规类型：
        1. 红灯时进入待转区 (waiting_area_red_entry)
        2. 非左转绿灯时驶离待转区 (waiting_area_illegal_exit)
        
        Args:
            track_id: 车辆追踪ID
            bbox: 边界框 (x1, y1, x2, y2)
            frame: 当前帧图像
            timestamp: 时间戳
            
        Returns:
            dict or None: 违规记录字典，如果没有违规则返回 None
        """
        # 计算车辆的多个关键点（底部四个角点 + 底部中心点）
        x1, y1, x2, y2 = bbox
        
        # 底部四个角点
        bottom_left = (int(x1), int(y2))
        bottom_right = (int(x2), int(y2))
        bottom_center = (int((x1 + x2) / 2), int(y2))
        
        # 底部中间的两个点（四等分）
        quarter_width = (x2 - x1) / 4
        bottom_quarter_left = (int(x1 + quarter_width), int(y2))
        bottom_quarter_right = (int(x2 - quarter_width), int(y2))
        
        # 用于检测的5个关键点
        check_points = [
            bottom_left,
            bottom_quarter_left,
            bottom_center,
            bottom_quarter_right,
            bottom_right
        ]
        
        # 用于记录的中心点
        vehicle_center = bottom_center
        
        # 遍历所有方向的待转区
        for direction, data in self.rois.items():
            # 跳过 solid_lines 字段
            if direction == 'solid_lines':
                continue
            
            # 检查该方向是否有待转区配置
            if 'left_turn_waiting_area' not in data or not data['left_turn_waiting_area']:
                continue
            
            # 检查车辆底部关键点是否在待转区内
            # 要求至少4个点在待转区内才判定为"在待转区内"，避免误判
            points_in_area = 0
            for waiting_area_poly in data['left_turn_waiting_area']:
                for point in check_points:
                    if self.is_point_in_polygon(point, waiting_area_poly):
                        points_in_area += 1
                if points_in_area > 0:  # 如果在某个待转区多边形内找到点，不需要检查其他多边形
                    break
            
            # 至少4个关键点在待转区内，才判定为"在待转区内"
            is_in_waiting_area = (points_in_area >= 4)
            
            # 获取该车辆在这个方向的待转区状态
            status = self.vehicle_waiting_area_status[track_id][direction]
            
            if is_in_waiting_area:
                # 车辆现在在待转区内
                if not status['inside']:
                    # 车辆刚进入待转区
                    if status['outside']:
                        # 车辆之前在待转区外，现在进入了
                        # 检查：红灯时进入待转区 = 违规！
                        if self.traffic_lights.get(direction) == 'red':
                            # 红灯时进入待转区 - 违规！
                            if not self._should_record_violation(track_id, 'waiting_area_red_entry', timestamp):
                                status['inside'] = True
                                status['enter_time'] = timestamp
                                return None  # 在冷却期内，跳过
                            
                            violation_id = f"WAIT_RED_{direction}_{track_id}_{int(timestamp)}"
                            screenshot_path = self.save_violation_screenshot(
                                frame, bbox, violation_id, "waiting_area"
                            )
                            
                            violation_record = {
                                'id': violation_id,
                                'type': 'waiting_area_red_entry',
                                'track_id': track_id,
                                'direction': direction,
                                'timestamp': datetime.fromtimestamp(timestamp).isoformat(),
                                'location': vehicle_center,
                                'bbox': bbox,
                                'screenshot': str(screenshot_path),
                                'description': '红灯时进入左转待转区'
                            }
                            
                            self.violations.append(violation_record)
                            print(f"⚠️ [待转区违规-红灯进入] Track {track_id} @ {direction}")
                            
                            # 上报到后端 API
                            self._report_to_backend(violation_record, frame)
                            
                            status['inside'] = True
                            status['enter_time'] = timestamp
                            return violation_record
                    
                    # 标记为在待转区内
                    status['inside'] = True
                    if status['enter_time'] is None:
                        status['enter_time'] = timestamp
                        
            else:
                # 车辆不在待转区内
                if status['inside']:
                    # 车辆刚离开待转区
                    # 检查：非左转绿灯时离开待转区 = 违规！
                    left_turn_signal = self.left_turn_signals.get(direction, 'red')
                    if left_turn_signal != 'green':
                        # 非左转绿灯时离开待转区 - 违规！
                        if not self._should_record_violation(track_id, 'waiting_area_illegal_exit', timestamp):
                            status['inside'] = False
                            return None  # 在冷却期内，跳过
                        
                        violation_id = f"WAIT_EXIT_{direction}_{track_id}_{int(timestamp)}"
                        screenshot_path = self.save_violation_screenshot(
                            frame, bbox, violation_id, "waiting_area"
                        )
                        
                        violation_record = {
                            'id': violation_id,
                            'type': 'waiting_area_illegal_exit',
                            'track_id': track_id,
                            'direction': direction,
                            'timestamp': datetime.fromtimestamp(timestamp).isoformat(),
                            'location': vehicle_center,
                            'bbox': bbox,
                            'screenshot': str(screenshot_path),
                            'description': f'非左转绿灯时驶离待转区 (当前左转信号: {left_turn_signal})'
                        }
                        
                        self.violations.append(violation_record)
                        print(f"⚠️ [待转区违规-非法驶离] Track {track_id} @ {direction} (左转信号: {left_turn_signal})")
                        
                        # 上报到后端 API
                        self._report_to_backend(violation_record, frame)
                        
                        status['inside'] = False
                        return violation_record
                    
                    # 正常离开（左转绿灯）
                    status['inside'] = False
                
                # 标记车辆曾在待转区外
                status['outside'] = True
        
        return None

    def get_violation_summary(self):
        """
        获取违规统计摘要

        Returns:
            dict: 统计信息
        """
        red_light_count = sum(1 for v in self.violations if v['type'] == 'red_light_running')
        wrong_way_count = sum(1 for v in self.violations if v['type'] == 'wrong_way_driving')
        lane_change_count = sum(1 for v in self.violations if v['type'] == 'lane_change_across_solid_line')
        waiting_red_entry_count = sum(1 for v in self.violations if v['type'] == 'waiting_area_red_entry')
        waiting_illegal_exit_count = sum(1 for v in self.violations if v['type'] == 'waiting_area_illegal_exit')

        return {
            'total_violations': len(self.violations),
            'red_light_running': red_light_count,
            'wrong_way_driving': wrong_way_count,
            'lane_change_across_solid_line': lane_change_count,
            'waiting_area_red_entry': waiting_red_entry_count,
            'waiting_area_illegal_exit': waiting_illegal_exit_count,
            'violations': self.violations
        }

    def _convert_to_serializable(self, obj):
        """
        将numpy类型转换为Python原生类型，以便JSON序列化

        Args:
            obj: 要转换的对象

        Returns:
            转换后的对象
        """
        if isinstance(obj, dict):
            return {key: self._convert_to_serializable(value) for key, value in obj.items()}
        elif isinstance(obj, list):
            return [self._convert_to_serializable(item) for item in obj]
        elif isinstance(obj, tuple):
            return tuple(self._convert_to_serializable(item) for item in obj)
        elif isinstance(obj, (np.integer, np.int64, np.int32)):
            return int(obj)
        elif isinstance(obj, (np.floating, np.float64, np.float32)):
            return float(obj)
        elif isinstance(obj, np.ndarray):
            return obj.tolist()
        else:
            return obj

    def export_violations(self, output_path: str):
        """
        导出违规记录到JSON文件

        Args:
            output_path: 输出文件路径
        """
        summary = self.get_violation_summary()
        
        # 转换所有numpy类型为Python原生类型
        summary = self._convert_to_serializable(summary)

        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(summary, f, indent=2, ensure_ascii=False)

        print(f"✅ 违规记录已导出: {output_path}")


if __name__ == "__main__":
    # 简单测试
    print("=" * 60)
    print("  违规检测器测试（带后端API集成）")
    print("=" * 60)
    
    detector = ViolationDetector(
        rois_path="./data/rois.json",
        screenshot_dir="./violations",
        intersection_id=1,      # 路口ID
        enable_api=True         # 启用API上报
    )

    print(f"\n✅ 违规检测器初始化成功！")
    print(f"📊 加载了 {len(detector.rois)} 个方向的ROI配置")
    print(f"🔗 API上报: {'已启用' if detector.enable_api else '已禁用'}")
    print(f"🏢 路口ID: {detector.intersection_id}")
    print("=" * 60)
