"""
图片违规检测模块 - TrafficMind 交通智脑

功能：
1. 闯红灯检测 (Red Light Running Detection)
2. 跨实线变道检测 (Lane Change Across Solid Line Detection)

专门用于单张图片的违规检测，不需要追踪轨迹
"""

import cv2
import numpy as np
import json
from datetime import datetime
from pathlib import Path
from ultralytics import YOLO


class ImageViolationDetector:
    """单张图片违规检测器"""

    def __init__(
        self,
        rois_path: str,
        model_path: str = "yolov8s.pt",
        screenshot_dir: str = "./output/screenshots",
        intersection_id: int = 1,
        enable_api: bool = False
    ):
        """
        初始化图片违规检测器

        Args:
            rois_path: ROI配置文件路径 (rois.json)
            model_path: YOLOv8模型路径
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

        # 加载YOLOv8模型
        print(f"🔧 加载YOLOv8模型: {model_path}")
        self.model = YOLO(model_path)

        # 违规记录
        self.violations = []

        # API 集成配置
        self.intersection_id = intersection_id
        self.enable_api = enable_api
        self.api_client = None

        if enable_api:
            try:
                from backend_api_client import BackendAPIClient
                self.api_client = BackendAPIClient()
                if self.api_client.health_check():
                    print("[API]  后端连接成功")
                else:
                    print("[API] 后端连接失败")
                    self.enable_api = False
            except Exception as e:
                print(f"[API] API客户端初始化失败: {e}")
                self.enable_api = False

    def detect_vehicles(self, image, conf_threshold=0.15, debug=False):
        """
        检测图片中的车辆

        Args:
            image: 输入图片 (numpy array)
            conf_threshold: 置信度阈值
            debug: 是否显示调试信息

        Returns:
            list: 检测到的车辆列表 [(bbox, confidence), ...]
                 bbox = (x1, y1, x2, y2)
        """
        # YOLO检测
        results = self.model(image, conf=conf_threshold, verbose=False)

        vehicles = []
        # 车辆类别ID（COCO数据集）
        vehicle_classes = [2, 3, 5, 7]  # car, motorcycle, bus, truck

        for result in results:
            boxes = result.boxes
            if debug:
                print(f"  [YOLO] 检测到 {len(boxes)} 个目标 (置信度阈值: {conf_threshold})")

            for box in boxes:
                cls = int(box.cls[0])
                confidence = float(box.conf[0])

                if debug:
                    x1_d, y1_d, x2_d, y2_d = box.xyxy[0].cpu().numpy()
                    is_vehicle = "车辆" if cls in vehicle_classes else "非车辆"
                    print(f"  [YOLO] {is_vehicle} 类别: {cls}, 置信度: {confidence:.2f}, bbox: ({int(x1_d)},{int(y1_d)},{int(x2_d)},{int(y2_d)})")

                if cls in vehicle_classes:
                    # 获取边界框坐标
                    x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
                    vehicles.append(((int(x1), int(y1), int(x2), int(y2)), confidence))

        if debug and len(vehicles) == 0:
            print(f"  [YOLO] 警告: 没有检测到车辆! 尝试降低置信度阈值 (当前: {conf_threshold})")

        return vehicles

    def is_point_in_polygon(self, point, polygon):
        """判断点是否在多边形内"""
        poly = np.array(polygon, dtype=np.int32)
        result = cv2.pointPolygonTest(poly, point, False)
        return result >= 0

    def detect_red_light_violation(self, image, signal_states=None, debug=False):
        """
        检测闯红灯违规

        Args:
            image: 输入图片
            signal_states: 信号灯状态字典
                          例: {'north_bound': 'red', 'south_bound': 'green', ...}
                          如果为None，默认所有方向都是红灯
            debug: 是否显示调试信息

        Returns:
            list: 违规车辆列表
        """
        if signal_states is None:
            # 默认所有方向红灯
            signal_states = {
                'north_bound': 'red',
                'south_bound': 'red',
                'west_bound': 'red',
                'east_bound': 'red'
            }

        # 检测车辆
        vehicles = self.detect_vehicles(image, debug=debug)
        violations = []
        print("车辆 :", vehicles)
        if debug:
            print(f"  🔍 检测到 {len(vehicles)} 辆车")

        # 遍历每辆车
        for idx, (bbox, confidence) in enumerate(vehicles):
            x1, y1, x2, y2 = bbox

            if debug:
                print(f"  🚗 车辆 {idx}: bbox={bbox}, confidence={confidence:.2f}")

            # 检查车辆是否在停止线内(闯红灯)
            for direction, data in self.rois.items():
                if direction == 'solid_lines':
                    continue

                # 检查信号灯状态
                if signal_states.get(direction) != 'red':
                    if debug:
                        print(f"     → {direction}: 绿灯,跳过")
                    continue  # 不是红灯，跳过

                # 根据方向计算车头位置（20%位置）
                vehicle_width = x2 - x1
                vehicle_height = y2 - y1

                if direction == 'north_bound':
                    # 北向南：车从上往下开，车头在下方（y2）
                    vehicle_head_point = (int((x1 + x2) / 2), int(y2 - vehicle_height * 0.2))
                elif direction == 'south_bound':
                    # 南向北：车从下往上开，车头在上方（y1）
                    vehicle_head_point = (int((x1 + x2) / 2), int(y1 + vehicle_height * 0.2))
                elif direction == 'west_bound':
                    # 西向东：车从左往右开，车头在右方（x2）
                    vehicle_head_point = (int(x2 - vehicle_width * 0.2), int((y1 + y2) / 2))
                elif direction == 'east_bound':
                    # 东向西：车从右往左开，车头在左方（x1）
                    vehicle_head_point = (int(x1 + vehicle_width * 0.2), int((y1 + y2) / 2))
                else:
                    # 默认使用中心点
                    vehicle_head_point = (int((x1 + x2) / 2), int((y1 + y2) / 2))

                if debug:
                    print(f"     → {direction}: 车头位置={vehicle_head_point}")

                # 检查车辆是否在停止线区域内
                for stop_line_poly in data['stop_line']:
                    in_stop_area = self.is_point_in_polygon(vehicle_head_point, stop_line_poly)

                    if debug:
                        print(f"     → {direction}: 车辆{'在' if in_stop_area else '不在'}停止线区域内")

                    # 红灯时，车辆进入停止线区域 = 闯红灯
                    if in_stop_area:
                        # 在红灯状态下，车辆在停止线内 = 闯红灯
                        violation_id = f"RED_{direction}_{idx}_{int(datetime.now().timestamp())}"
                        screenshot_path = self.save_violation_screenshot(
                            image, bbox, violation_id, "red_light"
                        )

                        violation_record = {
                            'id': violation_id,
                            'type': 'red_light_running',
                            'vehicle_index': idx,
                            'direction': direction,
                            'confidence': confidence,
                            'timestamp': datetime.now().isoformat(),
                            'location': vehicle_head_point,
                            'bbox': bbox,
                            'screenshot': str(screenshot_path)
                        }

                        violations.append(violation_record)
                        if debug:
                            print(f"  [闯红灯] 车辆 {idx} @ {direction}, 置信度: {confidence:.2f}")

                        # 上报到后端
                        if self.enable_api:
                            self._report_to_backend(violation_record, image)

                        break

        return violations

    def detect_lane_change_violation(self, image, debug=False):
        """
        检测跨实线变道违规

        基于车辆与实线的位置关系，判断车辆是否压线

        Args:
            image: 输入图片
            debug: 是否显示调试信息

        Returns:
            list: 违规车辆列表
        """
        # 检测车辆
        vehicles = self.detect_vehicles(image)
        violations = []

        if debug:
            print(f"  🔍 检测到 {len(vehicles)} 辆车")

        # 获取实线配置
        solid_lines = self.rois.get('solid_lines', [])

        if debug:
            print(f"  📐 ROI配置中有 {len(solid_lines)} 条实线")
            for sl in solid_lines:
                print(f"     - {sl['name']}: {sl['coordinates']}")

        # 遍历每辆车
        for idx, (bbox, confidence) in enumerate(vehicles):
            x1, _, x2, y2 = bbox
            # 使用车辆底部中心点作为判断点(更准确)
            vehicle_bottom_center = (int((x1 + x2) / 2), int(y2))

            if debug:
                print(f"  🚗 车辆 {idx}: bbox={bbox}, bottom_center={vehicle_bottom_center}")

            # 检查车辆与每条实线的关系
            for solid_line in solid_lines:
                line_name = solid_line['name']
                coords = solid_line['coordinates']
                if len(coords) != 2:
                    continue

                # 计算车辆中心点到实线的距离
                dist = self._point_to_line_distance(vehicle_bottom_center, coords[0], coords[1])

                # 如果距离小于阈值，认为压线
                # 使用车辆bbox宽度作为参考，适当放宽阈值
                vehicle_width = x2 - x1
                threshold = vehicle_width * 0.3  # 车辆宽度的30% (放宽阈值)

                if debug:
                    print(f"     → {line_name}: 距离={dist:.1f}px, 阈值={threshold:.1f}px, 车宽={vehicle_width}px")

                if dist < threshold:
                    # 车辆压线！
                    violation_id = f"LANE_{line_name}_{idx}_{int(datetime.now().timestamp())}"
                    screenshot_path = self.save_violation_screenshot(
                        image, bbox, violation_id, "lane_change"
                    )

                    violation_record = {
                        'id': violation_id,
                        'type': 'lane_change_across_solid_line',
                        'vehicle_index': idx,
                        'solid_line': line_name,
                        'direction': solid_line['direction'],
                        'confidence': confidence,
                        'distance_to_line': float(dist),
                        'timestamp': datetime.now().isoformat(),
                        'location': vehicle_bottom_center,
                        'bbox': bbox,
                        'screenshot': str(screenshot_path)
                    }

                    violations.append(violation_record)
                    print(f"[跨实线变道] 车辆 {idx} 压线 {line_name}, 距离: {dist:.1f}px")

                    # 上报到后端
                    if self.enable_api:
                        self._report_to_backend(violation_record, image)

                    break  # 一辆车只记录一次压线违规

        return violations

    def _point_to_line_distance(self, point, line_p1, line_p2):
        """
        计算点到线段的最短距离

        Args:
            point: (x, y) 点坐标
            line_p1: 线段起点 (x1, y1)
            line_p2: 线段终点 (x2, y2)

        Returns:
            float: 距离
        """
        x0, y0 = point
        x1, y1 = line_p1
        x2, y2 = line_p2

        # 向量计算
        dx = x2 - x1
        dy = y2 - y1
        line_len_sq = dx * dx + dy * dy

        if line_len_sq == 0:
            # 线段退化为点
            return np.sqrt((x0 - x1)**2 + (y0 - y1)**2)

        # 计算投影参数t
        t = max(0, min(1, ((x0 - x1) * dx + (y0 - y1) * dy) / line_len_sq))

        # 投影点
        proj_x = x1 + t * dx
        proj_y = y1 + t * dy

        # 距离
        dist = np.sqrt((x0 - proj_x)**2 + (y0 - proj_y)**2)
        return dist

    def save_violation_screenshot(self, image, bbox, violation_id: str, vtype: str):
        """
        保存违规截图 - 只保存车辆及周边小范围区域

        Args:
            image: 原始图片
            bbox: 车辆边界框
            violation_id: 违规ID
            vtype: 违规类型

        Returns:
            Path: 截图保存路径
        """
        x1, y1, x2, y2 = bbox
        img_h, img_w = image.shape[:2]

        # 车辆尺寸
        car_w = x2 - x1
        car_h = y2 - y1

        # 较小的扩展区域（只扩展20%）
        expand_ratio = 0.2
        expand_w = int(car_w * expand_ratio)
        expand_h = int(car_h * expand_ratio)

        # 裁剪区域（只保留车辆及周边）
        crop_x1 = max(0, int(x1 - expand_w))
        crop_y1 = max(0, int(y1 - expand_h))
        crop_x2 = min(img_w, int(x2 + expand_w))
        crop_y2 = min(img_h, int(y2 + expand_h))

        # 确保裁剪区域足够大（至少200px）
        min_size = 200
        if crop_x2 - crop_x1 < min_size:
            center_x = (crop_x1 + crop_x2) // 2
            crop_x1 = max(0, center_x - min_size // 2)
            crop_x2 = min(img_w, center_x + min_size // 2)
        if crop_y2 - crop_y1 < min_size:
            center_y = (crop_y1 + crop_y2) // 2
            crop_y1 = max(0, center_y - min_size // 2)
            crop_y2 = min(img_h, center_y + min_size // 2)

        cropped_image = image[crop_y1:crop_y2, crop_x1:crop_x2].copy()

        # 绘制边界框
        box_x1 = int(x1 - crop_x1)
        box_y1 = int(y1 - crop_y1)
        box_x2 = int(x2 - crop_x1)
        box_y2 = int(y2 - crop_y1)

        # 红色边框
        cv2.rectangle(cropped_image, (box_x1, box_y1), (box_x2, box_y2), (0, 0, 255), 3)

        # 添加标签
        label = f"{vtype.upper()}"
        cv2.putText(
            cropped_image, label,
            (box_x1, max(15, box_y1 - 10)),
            cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 255), 2
        )

        # 保存
        filename = f"{violation_id}.jpg"
        filepath = self.screenshot_dir / filename
        cv2.imwrite(str(filepath), cropped_image)

        return filepath

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
            'lane_change_across_solid_line': 'CROSS_SOLID_LINE'
        }
        return mapping.get(violation_type, 'OTHER')

    def _report_to_backend(self, violation_record: dict, image=None):
        """上报违规到后端API"""
        if not self.enable_api or self.api_client is None:
            return

        try:
            screenshot_path = violation_record.get('screenshot', '')
            image_url = self.api_client.upload_image(screenshot_path) if screenshot_path else 'file:///no_image.jpg'

            timestamp_str = violation_record.get('timestamp', datetime.now().isoformat())
            direction = violation_record.get('direction', 'north_bound')
            vehicle_idx = violation_record.get('vehicle_index', 0)
            plate_number = f"UNIDENTIFIED_{vehicle_idx:03d}"    #车牌

            api_data = {
                'intersectionId': self.intersection_id,
                'direction': self._map_direction_to_api(direction),
                'turnType': 'STRAIGHT',
                'plateNumber': plate_number,
                'violationType': self._map_violation_type_to_api(violation_record.get('type', '')),
                'imageUrl': image_url,
                'aiConfidence': violation_record.get('confidence', 0.95),
                'occurredAt': timestamp_str
            }

            violation_id = self.api_client.report_violation(api_data)

            if violation_id:
                violation_record['backend_id'] = violation_id
                print(f"[API]  上报成功! 后端ID: {violation_id}")
            else:
                print(f"[API]  上报失败")

        except Exception as e:
            print(f"[API]  上报异常: {type(e).__name__}: {e}")

    def process_image(self, image_path: str, signal_states=None, detect_types=['red_light', 'lane_change'], debug=False):
        """
        处理单张图片，检测所有违规

        Args:
            image_path: 图片路径
            signal_states: 信号灯状态（仅闯红灯检测需要）
            detect_types: 要检测的违规类型列表
                         ['red_light', 'lane_change']
            debug: 是否显示调试信息

        Returns:
            dict: 检测结果
        """
        print(f"\n📸 处理图片: {image_path}")

        # 读取图片
        image = cv2.imread(image_path)
        if image is None:
            print(f" 无法读取图片: {image_path}")
            return None

        print(f"  图片尺寸: {image.shape[1]}x{image.shape[0]}")

        all_violations = []

        # 1. 闯红灯检测
        if 'red_light' in detect_types:
            print("\n🔍 检测闯红灯...")
            red_light_violations = self.detect_red_light_violation(image, signal_states, debug=debug)
            all_violations.extend(red_light_violations)
            print(f"  发现 {len(red_light_violations)} 个闯红灯违规")

        # 2. 跨实线变道检测
        if 'lane_change' in detect_types:
            print("\n🔍 检测跨实线变道...")
            lane_change_violations = self.detect_lane_change_violation(image, debug=debug)
            all_violations.extend(lane_change_violations)
            print(f"  发现 {len(lane_change_violations)} 个跨实线变道违规")

        # 保存所有违规
        self.violations.extend(all_violations)

        # 绘制标注图片
        annotated_image = image.copy()
        for violation in all_violations:
            bbox = violation.get('bbox')
            if bbox:
                x1, y1, x2, y2 = map(int, bbox)
                # 绘制边界框 (红色)
                cv2.rectangle(annotated_image, (x1, y1), (x2, y2), (0, 0, 255), 3)

                # 添加违规类型标签
                label = violation['type'].replace('_', ' ').title()
                cv2.putText(annotated_image, label, (x1, y1 - 10),
                           cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)

        # 统计结果
        result = {
            'image_path': image_path,
            'total_violations': len(all_violations),
            'red_light_violations': sum(1 for v in all_violations if v['type'] == 'red_light_running'),
            'lane_change_violations': sum(1 for v in all_violations if v['type'] == 'lane_change_across_solid_line'),
            'violations': all_violations,
            'annotated_image': annotated_image  # 新增：返回标注后的图片
        }

        print(f"\n检测完成: 共发现 {len(all_violations)} 个违规")
        return result

    def process_image_data(self, image, image_name='image.jpg', signal_states=None, detect_types=['red_light', 'lane_change'], debug=False):
        """
        处理图片数据（numpy array），检测所有违规
        用于 Flask API 接收上传的图片

        Args:
            image: 图片数据 (numpy array)
            image_name: 图片名称（用于日志）
            signal_states: 信号灯状态（仅闯红灯检测需要）
            detect_types: 要检测的违规类型列表
            debug: 是否显示调试信息

        Returns:
            dict: 检测结果
        """
        print(f"\n📸 处理图片: {image_name}")
        print(f"  图片尺寸: {image.shape[1]}x{image.shape[0]}")

        all_violations = []

        # 1. 闯红灯检测
        if 'red_light' in detect_types:
            print("\n🔍 检测闯红灯...")
            red_light_violations = self.detect_red_light_violation(image, signal_states, debug=debug)
            all_violations.extend(red_light_violations)
            print(f"  发现 {len(red_light_violations)} 个闯红灯违规")

        # 2. 跨实线变道检测
        if 'lane_change' in detect_types:
            print("\n🔍 检测跨实线变道...")
            lane_violations = self.detect_lane_change_violation(image, debug=debug)
            all_violations.extend(lane_violations)
            print(f"  发现 {len(lane_violations)} 个跨实线变道违规")

        # 绘制标注图片
        annotated_image = image.copy()
        for violation in all_violations:
            bbox = violation.get('bbox')
            if bbox:
                x1, y1, x2, y2 = map(int, bbox)
                # 绘制边界框 (红色)
                cv2.rectangle(annotated_image, (x1, y1), (x2, y2), (0, 0, 255), 3)

                # 添加违规类型标签
                label = violation['type'].replace('_', ' ').title()
                cv2.putText(annotated_image, label, (x1, y1 - 10),
                           cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 255), 2)

        # 统计结果
        result = {
            'image_name': image_name,
            'total_violations': len(all_violations),
            'red_light_violations': len([v for v in all_violations if v['type'] == 'red_light_running']),
            'lane_change_violations': len([v for v in all_violations if v['type'] == 'lane_change_across_solid_line']),
            'violations': all_violations,
            'annotated_image': annotated_image  # 新增：返回标注后的图片
        }

        # 保存到实例变量
        self.violations.extend(all_violations)

        print(f"\n📊 检测完成: 共发现 {len(all_violations)} 个违规")
        return result

    def export_violations(self, output_path: str):
        """导出违规记录到JSON文件"""
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(self.violations, f, indent=2, ensure_ascii=False)
        print(f" 违规记录已导出: {output_path}")


if __name__ == "__main__":
    # 测试代码
    print("=" * 60)
    print("  图片违规检测器测试")
    print("=" * 60)

    # 初始化检测器
    detector = ImageViolationDetector(
        rois_path="./data/rois.json",
        model_path="yolov8s.pt",
        screenshot_dir="./output/screenshots",
        enable_api=False
    )

    print("\n 图片违规检测器初始化成功！")
    print("支持的检测类型:")
    print("  1. 闯红灯检测 (red_light)")
    print("  2. 跨实线变道检测 (lane_change)")
    print("=" * 60)
