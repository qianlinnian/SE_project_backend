"""
车辆检测与追踪模块 - TrafficMind 交通智脑

功能：
1. 使用 YOLOv8 检测车辆
2. 使用 DeepSORT 进行多目标追踪
"""

import cv2
import numpy as np
from ultralytics import YOLO
from collections import defaultdict


class VehicleTracker:
    """车辆检测与追踪器"""

    def __init__(self, model_path: str = "yolov8s.pt", conf_threshold: float = 0.25, iou_threshold: float = 0.45):
        """
        初始化车辆追踪器

        Args:
            model_path: YOLOv8 模型路径（默认使用预训练的 yolov8s.pt）
                       推荐: yolov8s.pt (更好的检测效果) 或 yolov8m.pt (平衡)
            conf_threshold: 置信度阈值 (降低可减少漏检，0.2-0.3推荐)
            iou_threshold: IOU阈值用于NMS (降低可保留更多重叠目标)
        """
        print(f"🚀 加载 YOLOv8 模型: {model_path}")
        self.model = YOLO(model_path)
        self.conf_threshold = conf_threshold
        self.iou_threshold = iou_threshold

        # 车辆类别 (COCO数据集)
        self.vehicle_classes = [2, 3, 5, 7]  # car, motorcycle, bus, truck

        # 简单的轨迹追踪（使用 YOLOv8 内置的追踪）
        self.track_history = defaultdict(list)

        print("车辆追踪器初始化成功！")

    def detect_and_track(self, frame):
        """
        检测并追踪车辆

        Args:
            frame: 输入帧图像

        Returns:
            list: 追踪结果 [(track_id, bbox), ...]
                  bbox = (x1, y1, x2, y2)
        """
        # 使用 YOLOv8 的内置追踪功能
        results = self.model.track(
            frame,
            persist=True,  # 持久追踪
            conf=self.conf_threshold,  # 置信度阈值
            iou=self.iou_threshold,    # IOU阈值
            classes=self.vehicle_classes,  # 只检测车辆
            verbose=False
        )

        detections = []

        if results[0].boxes is not None and results[0].boxes.id is not None:
            # 获取边界框、追踪ID、置信度
            boxes = results[0].boxes.xyxy.cpu().numpy()  # (x1, y1, x2, y2)
            track_ids = results[0].boxes.id.cpu().numpy().astype(int)
            confidences = results[0].boxes.conf.cpu().numpy()
            classes = results[0].boxes.cls.cpu().numpy().astype(int)

            for box, track_id, conf, cls in zip(boxes, track_ids, confidences, classes):
                x1, y1, x2, y2 = box
                detections.append((track_id, (x1, y1, x2, y2)))

                # 记录轨迹历史
                center = ((x1 + x2) / 2, (y1 + y2) / 2)
                self.track_history[track_id].append(center)

                # 只保留最近30帧的轨迹
                if len(self.track_history[track_id]) > 30:
                    self.track_history[track_id].pop(0)
        else:
            # 检测到物体但追踪器未分配ID - 这是正常的，通常几帧后会建立追踪
            pass

        return detections

    def draw_detections(self, frame, detections):
        """
        在图像上绘制检测结果

        Args:
            frame: 输入帧图像
            detections: 检测结果 [(track_id, bbox), ...]

        Returns:
            frame: 绘制后的图像
        """
        for track_id, (x1, y1, x2, y2) in detections:
            # 绘制边界框
            cv2.rectangle(frame, (int(x1), int(y1)), (int(x2), int(y2)), (0, 255, 0), 2)

            # 绘制 ID 标签
            label = f"ID:{track_id}"
            cv2.putText(
                frame, label, (int(x1), int(y1) - 10),
                cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2
            )

            # 绘制轨迹
            if track_id in self.track_history:
                points = np.array(self.track_history[track_id], dtype=np.int32)
                cv2.polylines(frame, [points], False, (255, 0, 0), 2)

        return frame


class SimpleTrafficLightDetector:
    """
    简单的信号灯检测器

    方案1: 使用固定循环周期模拟（用于测试）
    方案2: 后续可以用训练好的 YOLOv8 信号灯模型替换
    """

    def __init__(self, cycle_seconds: float = 30.0):
        """
        初始化信号灯检测器（模拟模式）

        Args:
            cycle_seconds: 信号灯循环周期（秒）
        """
        self.cycle_seconds = cycle_seconds
        self.start_time = None
        self.previous_states = {}  # 缓存上一次的状态，用于检测变化

        print(f"信号灯模拟器已启动（周期: {cycle_seconds}秒）")

    def get_signal_states(self, current_time):
        """
        获取当前各方向的信号灯状态（模拟）

        模拟逻辑：
        - 南北方向（north_bound, south_bound）和东西方向（west_bound, east_bound）交替
        - 周期的前一半：南北绿灯，东西红灯
        - 周期的后一半：南北红灯，东西绿灯

        Args:
            current_time: 当前时间戳

        Returns:
            tuple: (states_dict, changed_bool)
                - states_dict: {'north_bound': 'green', 'south_bound': 'green', ...}
                - changed_bool: True 如果状态发生变化
        """
        if self.start_time is None:
            self.start_time = current_time

        elapsed = (current_time - self.start_time) % self.cycle_seconds
        half_cycle = self.cycle_seconds / 2

        if elapsed < half_cycle:
            # 南北红灯，东西绿灯（改为初始红灯测试闯红灯）
            current_states = {
                'north_bound': 'red',
                'south_bound': 'red',
                'west_bound': 'green',
                'east_bound': 'green'
            }
        else:
            # 南北绿灯，东西红灯
            current_states = {
                'north_bound': 'green',
                'south_bound': 'green',
                'west_bound': 'red',
                'east_bound': 'red'
            }
        
        # 检测状态是否变化
        changed = current_states != self.previous_states
        self.previous_states = current_states.copy()
        
        return current_states, changed


if __name__ == "__main__":
    # 测试车辆追踪器
    print("=" * 50)
    print("🧪 测试车辆追踪器")
    print("=" * 50)

    tracker = VehicleTracker(model_path="yolov8s.pt")

    # 加载测试图片
    frame = cv2.imread("./data/background.png")
    if frame is None:
        print("无法加载测试图片")
    else:
        print("开始检测...")
        detections = tracker.detect_and_track(frame)
        print(f"检测到 {len(detections)} 辆车")

        # 绘制结果
        result_frame = tracker.draw_detections(frame, detections)
        cv2.imwrite("./data/detection_result.png", result_frame)
        print("检测结果已保存: ./data/detection_result.png")
