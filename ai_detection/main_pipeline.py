"""
完整的交通违规检测管道 - TrafficMind 交通智脑

将所有模块整合：
1. 车辆检测与追踪 (vehicle_tracker.py)
2. 违规检测 (violation_detector.py)
3. 信号灯状态管理
4. 视频处理与可视化
"""

import cv2
import time
import argparse
from pathlib import Path
import json

from core.vehicle_tracker import VehicleTracker, SimpleTrafficLightDetector
from core.violation_detector import ViolationDetector


class TrafficViolationPipeline:
    """完整的交通违规检测管道"""

    def __init__(
        self,
        rois_path: str,
        model_path: str = "yolov8s.pt",
        screenshot_dir: str = "./violations",
        signal_cycle: float = 60.0
    ):
        """
        初始化检测管道

        Args:
            rois_path: ROI配置文件路径
            model_path: YOLOv8模型路径
            screenshot_dir: 违规截图保存目录
            signal_cycle: 信号灯循环周期（秒），默认60秒
        """
        print("🚀 初始化交通违规检测管道...")
        print("=" * 60)

        # 1. 初始化车辆追踪器（降低置信度减少漏检）
        self.tracker = VehicleTracker(model_path=model_path, conf_threshold=0.25)

        # 2. 初始化违规检测器
        self.violation_detector = ViolationDetector(
            rois_path=rois_path,
            screenshot_dir=screenshot_dir
        )

        # 3. 初始化信号灯检测器（模拟模式）
        self.traffic_light = SimpleTrafficLightDetector(cycle_seconds=signal_cycle)

        print("=" * 60)
        print("所有模块初始化完成！\n")

    def process_video(self, video_path: str, output_path: str = None, display: bool = True):
        """
        处理视频文件

        Args:
            video_path: 输入视频路径
            output_path: 输出视频路径（可选）
            display: 是否实时显示
        """
        print(f"📹 开始处理视频: {video_path}")

        # 打开视频
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            print(f"无法打开视频: {video_path}")
            return

        # 获取视频信息
        fps = int(cap.get(cv2.CAP_PROP_FPS))
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))

        print(f"  分辨率: {width}x{height}")
        print(f"  帧率: {fps} FPS")
        print(f"  总帧数: {total_frames}")

        # 创建视频写入器（如果需要保存）
        writer = None
        if output_path:
            fourcc = cv2.VideoWriter_fourcc(*'mp4v')
            writer = cv2.VideoWriter(output_path, fourcc, fps, (width, height))
            print(f"  输出视频: {output_path}")

        print("\n🎬 开始处理...\n")

        frame_count = 0
        start_time = time.time()

        while True:
            ret, frame = cap.read()
            if not ret:
                break

            frame_count += 1
            current_time = time.time()

            # ========== 1. 车辆检测与追踪 ==========
            detections = self.tracker.detect_and_track(frame)

            # ========== 2. 更新信号灯状态 ==========
            signal_states, signal_changed = self.traffic_light.get_signal_states(current_time)
            for direction, state in signal_states.items():
                # 只在信号灯状态变化时打印
                self.violation_detector.update_signal_state(direction, state, force_print=signal_changed)

            # ========== 3. 违规检测 ==========
            violations = self.violation_detector.process_frame(
                frame, detections, current_time
            )

            # ========== 4. 可视化 ==========
            vis_frame = self._visualize(frame, detections, signal_states, violations)

            # 保存输出视频
            if writer:
                writer.write(vis_frame)

            # 实时显示
            if display:
                cv2.imshow("Traffic Violation Detection", vis_frame)
                if cv2.waitKey(1) & 0xFF == ord('q'):
                    print("\n⏸️ 用户中断")
                    break

            # 进度显示
            if frame_count % 30 == 0:
                elapsed = time.time() - start_time
                fps_actual = frame_count / elapsed
                progress = (frame_count / total_frames) * 100
                print(f"  处理进度: {frame_count}/{total_frames} ({progress:.1f}%) | "
                      f"FPS: {fps_actual:.1f} | 违规: {len(self.violation_detector.violations)}")

        # 清理
        cap.release()
        if writer:
            writer.release()
        cv2.destroyAllWindows()

        # 统计结果
        elapsed_time = time.time() - start_time
        print("\n" + "=" * 60)
        print("处理完成！")
        print("=" * 60)
        print(f"  总帧数: {frame_count}")
        print(f"  处理时间: {elapsed_time:.2f}秒")
        print(f"  平均FPS: {frame_count / elapsed_time:.2f}")
        print(f"\n违规统计:")

        summary = self.violation_detector.get_violation_summary()
        print(f"   总违规数: {summary['total_violations']}")
        print(f"    - 闯红灯: {summary['red_light_running']}")
        print(f"    - 逆行: {summary['wrong_way_driving']}")
        print(f"    - 跨实线变道: {summary['lane_change_across_solid_line']}")
        print(f"    - 红灯进入待转区: {summary['waiting_area_red_entry']}")
        print(f"    - 非法驶离待转区: {summary['waiting_area_illegal_exit']}")

        # 导出违规记录（保存到violations文件夹）
        violations_dir = Path("./violations")
        violations_dir.mkdir(exist_ok=True)  # 确保文件夹存在
        violation_json = violations_dir / (str(Path(video_path).stem) + "_violations.json")
        self.violation_detector.export_violations(str(violation_json))

        return summary

    def _visualize(self, frame, detections, signal_states, violations):
        """
        可视化检测结果

        Args:
            frame: 原始帧
            detections: 车辆检测结果
            signal_states: 信号灯状态
            violations: 本帧违规列表

        Returns:
            可视化后的帧
        """
        vis_frame = frame.copy()

        # 1. 绘制车辆检测框和轨迹
        vis_frame = self.tracker.draw_detections(vis_frame, detections)

        # 2. 绘制信号灯状态面板
        self._draw_signal_panel(vis_frame, signal_states)

        # 3. 绘制违规警告
        if violations:
            cv2.putText(
                vis_frame, f"VIOLATION DETECTED! Count: {len(violations)}",
                (10, 100), cv2.FONT_HERSHEY_SIMPLEX, 1.2, (0, 0, 255), 3
            )

        # 4. 绘制统计信息
        self._draw_statistics(vis_frame)

        return vis_frame

    def _draw_signal_panel(self, frame, signal_states):
        """绘制信号灯状态面板"""
        y_offset = 30
        for i, (direction, state) in enumerate(signal_states.items()):
            color = (0, 255, 0) if state == 'green' else (0, 0, 255)
            text = f"{direction}: {state.upper()}"
            cv2.putText(
                frame, text, (10, y_offset + i * 30),
                cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 2
            )

    def _draw_statistics(self, frame):
        """绘制统计信息"""
        summary = self.violation_detector.get_violation_summary()
        y_offset = frame.shape[0] - 60

        stats_text = f"Total Violations: {summary['total_violations']} | " \
                     f"Red: {summary['red_light_running']} | " \
                     f"Wrong Way: {summary['wrong_way_driving']} | " \
                     f"Lane Change: {summary['lane_change_across_solid_line']}"

        cv2.putText(
            frame, stats_text, (10, y_offset),
            cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2
        )


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="TrafficMind - 交通违规检测系统")
    parser.add_argument("--video", type=str, required=True, help="输入视频路径")
    parser.add_argument("--rois", type=str, default="./data/rois.json", help="ROI配置文件")
    parser.add_argument("--model", type=str, default="yolov8s.pt", help="YOLOv8模型路径（默认yolov8s.pt，速度更快）")
    parser.add_argument("--output", type=str, default=None, help="输出视频路径")
    parser.add_argument("--no-display", action="store_true", help="不实时显示")
    parser.add_argument("--signal-cycle", type=float, default=60.0, help="信号灯周期(秒)，默认60秒")

    args = parser.parse_args()

    # 创建管道
    pipeline = TrafficViolationPipeline(
        rois_path=args.rois,
        model_path=args.model,
        signal_cycle=args.signal_cycle
    )

    # 处理视频
    pipeline.process_video(
        video_path=args.video,
        output_path=args.output,
        display=not args.no_display
    )


if __name__ == "__main__":
    main()
