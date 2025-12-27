"""
完整的交通违规检测管道 - 手动控制版本
适用于自制测试视频（无信号灯、无车牌）
```python main_pipeline_manual.py --video YOUR_VIDEO.mp4 --rotation 90```bash**快速解决**：- **推荐在采集数据时保证视角正常**- **使用 `--rotation` 参数即可解决**- **90度旋转导致检测失效是正常现象**## 总结---   - 严重模糊？   - 车辆太小？   - 分辨率太低？3. **检查视频质量**   ```   # 置信度已默认设为0.25   python main_pipeline_manual.py --video data/test.mp4 --rotation 90   # 降低置信度   ```bash2. **检查置信度阈值**   ```   python -c "import cv2; cap=cv2.VideoCapture('data/test.mp4'); ret,f=cap.read(); cv2.imwrite('frame.jpg',cv2.rotate(f,cv2.ROTATE_90_CLOCKWISE)); print('已保存frame.jpg，检查车辆方向')"   # 查看旋转后的视频   ```bash1. **确认旋转角度正确**### 问题：旋转后还是检测不到## 🐛 故障排查---```done    python main_pipeline_manual.py --video "$video"for video in fixed_*.mp4; do# 再批量检测done    python Utility/video_rotator.py --input "$video" --output "fixed_$(basename $video)" --angle 90for video in data/*.mp4; do# 先批量旋转所有视频```bash### 场景3：批量处理- 避免倾斜- 水平安装确保摄像头安装角度正确：### 场景2：监控摄像头```python main_pipeline_manual.py --video data/phone.mp4 --rotation -90# 手机竖屏 → 需要旋转```bash手机竖屏拍摄会导致90度旋转：### 场景1：手机拍摄的视频## 💡 实际应用建议---- 如果视频旋转了，用软件旋转回来（成本低、效果好）- 在数据采集时保证视频是正常视角**更好的方案**：3. 通用性差（只适用于特定视角）2. 训练成本高（时间、算力）1. 需要收集大量旋转视角的车辆数据**可以，但不推荐**：### 能否训练识别旋转车辆的模型？   - 竖直目标无法匹配预设anchor   - YOLOv8的anchor设计针对水平目标3. **Anchor Box问题**   - 模型会误判为其他物体   - 旋转90度：高 > 宽 (如 100x200像素)   - 水平车辆：宽 > 高 (如 200x100像素)2. **特征不匹配**   - 模型学习的特征：车头、车尾、轮子的**水平排列**模式   - COCO数据集：99%的车辆都是水平方向1. **训练数据偏差**### 为什么不能直接识别旋转的车辆？## 🔧 技术细节---**结论**：旋转超过30度就会严重影响检测，**必须先旋转回正常视角**。| 180° | 20-30% | 严重失效 || 90° | 5-10% | 几乎失效 || ±30° | 60-70% | ⚠️ 明显下降 || ±15° | 85-90% | 可接受 || 0° (正常) | 95%+ | 最佳 ||---------|-----------|------|| 旋转角度 | 车辆检测率 | 说明 |## 旋转对检测的影响---```python main_pipeline_manual.py --video data/test.mp4 --rotation -90  # 逆时针90°python main_pipeline_manual.py --video data/test.mp4 --rotation 90   # 顺时针90°python main_pipeline_manual.py --video data/test.mp4 --rotation 0    # 不旋转# 试试不同角度，看哪个检测效果好```bash### 测试法3. 如果是**竖向**行驶（↑ ↓方向），需要旋转2. 车辆应该是**横向**行驶（← →方向）1. 打开视频看一眼### 视觉判断法## 🎯 如何判断需要旋转多少度？---- 可用于其他用途- 避免重复旋转- 保存修正后的视频**优点**：- `270`：顺时针旋转270度（= 逆时针90度）- `180`：翻转180度- `-90`：逆时针旋转90度  - `90`：顺时针旋转90度**旋转角度说明**：```python main_pipeline_manual.py --video data/fixed.mp4# 步骤2：用旋转后的视频检测python Utility/video_rotator.py --input data/rotated.mp4 --output data/fixed.mp4 --angle 90# 步骤1：旋转视频```bash**适用场景**：需要保存旋转后的视频，或多次使用### 方案2：预处理视频（离线处理）---- 简单方便- 实时处理- 不需要预处理视频**优点**：4. 输出结果3. 用正常视角检测车辆2. 自动旋转回正常视角1. 读取每一帧**工作原理**：```python main_pipeline_manual.py --video data/rotated.mp4 --rotation 180# 视频倒置180度python main_pipeline_manual.py --video data/rotated.mp4 --rotation -90# 视频被顺时针旋转了90度，需要逆时针旋转回来  python main_pipeline_manual.py --video data/rotated.mp4 --rotation 90# 视频被逆时针旋转了90度，需要顺时针旋转回来```bash**适用场景**：视频本身是旋转的，需要在检测前自动纠正### 方案1：使用命令行参数自动旋转（推荐）⭐## 解决方案---```                ↕️旋转90度:      🚗  (竖向车辆，模型从未见过)正常训练数据:  🚗 🚙 🚕  (横向车辆)```旋转90度后，车辆形状、长宽比、特征完全不匹配，模型无法识别。- 从未见过**竖直方向**的车辆（旋转90度）- 只见过**水平方向**的车辆（正常行驶视角）YOLOv8预训练模型（COCO数据集）训练时：### 原因**旋转90度后YOLOv8无法识别车辆 - 这是正常现象！**
特点：
1. 键盘实时控制信号灯状态
2. 或使用时间轴配置文件
3. 不需要车牌识别
4. 使用车辆追踪ID作为唯一标识
"""

import cv2
import time
import argparse
from pathlib import Path
import json

from core.vehicle_tracker import VehicleTracker
from core.violation_detector import ViolationDetector
from manual_signal_controller import ManualSignalController


class TrafficViolationPipelineManual:
    """手动控制版本的交通违规检测管道"""

    def __init__(
        self,
        rois_path: str,
        model_path: str = "yolov8s.pt",
        screenshot_dir: str = "./violations",
        signal_config: str = None,
        default_signal: str = "red",  # 改为默认红灯，方便测试闯红灯
        rotation_angle: int = 0  # 视频旋转角度 (0, 90, -90, 180)
    ):
        """
        初始化检测管道

        Args:
            rois_path: ROI配置文件路径
            model_path: YOLOv8模型路径
            screenshot_dir: 违规截图保存目录
            signal_config: 信号灯时间轴配置文件（可选）
            default_signal: 默认信号灯状态
            rotation_angle: 视频旋转角度，用于修正旋转的视频 (0, 90, -90, 180, 270)
        """
        print("🚀 初始化交通违规检测管道（手动控制版本）...")
        print("=" * 60)

        # 视频旋转角度
        self.rotation_angle = rotation_angle
        if rotation_angle != 0:
            print(f"🔄 视频将自动旋转 {rotation_angle}° 后再检测")
        
        # 1. 初始化车辆追踪器（降低置信度减少漏检）
        self.tracker = VehicleTracker(model_path=model_path, conf_threshold=0.25)

        # 2. 初始化违规检测器
        self.violation_detector = ViolationDetector(
            rois_path=rois_path,
            screenshot_dir=screenshot_dir
        )

        # 3. 初始化手动信号灯控制器
        self.signal_controller = ManualSignalController(
            config_path=signal_config,
            default_state=default_signal
        )

        print("=" * 60)
        print("所有模块初始化完成！\n")

        # 打印控制说明
        self.signal_controller.print_controls()

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
        if fps == 0:
            fps = 30  # 默认帧率
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
        video_start_time = time.time()

        while True:
            ret, frame = cap.read()
            if not ret:
                break

            # 如果需要旋转视频
            if self.rotation_angle != 0:
                if self.rotation_angle == 90 or self.rotation_angle == -270:
                    frame = cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
                elif self.rotation_angle == -90 or self.rotation_angle == 270:
                    frame = cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)
                elif self.rotation_angle == 180 or self.rotation_angle == -180:
                    frame = cv2.rotate(frame, cv2.ROTATE_180)

            frame_count += 1
            current_time = time.time()

            # 计算视频时间（用于时间轴配置）
            video_time = (frame_count - 1) / fps

            # ========== 1. 车辆检测与追踪 ==========
            detections = self.tracker.detect_and_track(frame)

            # ========== 2. 获取信号灯状态 ==========
            # 如果有时间轴配置，根据视频时间获取
            # 否则使用手动设置的状态
            signal_states = self.signal_controller.get_signal_states(video_time)
            left_turn_signals = self.signal_controller.get_left_turn_signals(video_time)

            # 更新违规检测器的信号灯状态
            for direction, state in signal_states.items():
                self.violation_detector.traffic_lights[direction] = state
            
            # 更新左转信号灯状态
            for direction, state in left_turn_signals.items():
                self.violation_detector.left_turn_signals[direction] = state

            # ========== 3. 违规检测 ==========
            violations = self.violation_detector.process_frame(
                frame, detections, current_time
            )

            # ========== 4. 可视化 ==========
            vis_frame = self._visualize(frame, detections, signal_states, left_turn_signals, violations, video_time)

            # 保存输出视频
            if writer:
                writer.write(vis_frame)

            # 实时显示
            if display:
                cv2.imshow("Traffic Violation Detection [按Q退出]", vis_frame)
                key = cv2.waitKey(1) & 0xFF

                # 处理键盘输入
                if key == ord('q'):
                    print("\n⏸️ 用户中断")
                    break
                else:
                    # 尝试处理信号灯控制键
                    self.signal_controller.handle_keyboard(key)

            # 进度显示
            if frame_count % 30 == 0 or frame_count == 1:
                elapsed = time.time() - start_time
                fps_actual = frame_count / elapsed if elapsed > 0 else 0
                progress = (frame_count / total_frames) * 100 if total_frames > 0 else 0
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

    def _visualize(self, frame, detections, signal_states, left_turn_signals, violations, video_time):
        """
        可视化检测结果

        Args:
            frame: 原始帧
            detections: 车辆检测结果
            signal_states: 信号灯状态
            left_turn_signals: 左转信号灯状态
            violations: 本帧违规列表
            video_time: 视频时间（秒）

        Returns:
            可视化后的帧
        """
        vis_frame = frame.copy()

        # 1. 绘制车辆检测框和轨迹
        vis_frame = self.tracker.draw_detections(vis_frame, detections)

        # 2. 绘制信号灯状态面板
        self._draw_signal_panel(vis_frame, signal_states, left_turn_signals)

        # 3. 绘制违规警告
        if violations:
            cv2.putText(
                vis_frame, f"!!! VIOLATION DETECTED !!! Count: {len(violations)}",
                (10, 120), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (0, 0, 255), 3
            )

        # 4. 绘制统计信息
        self._draw_statistics(vis_frame, video_time)

        # 5. 绘制控制提示
        self._draw_controls_hint(vis_frame)

        return vis_frame

    def _draw_signal_panel(self, frame, signal_states, left_turn_signals):
        """绘制信号灯状态面板（右上角）"""
        panel_x = frame.shape[1] - 350
        panel_y = 30

        # 标题
        cv2.putText(
            frame, "Signal Lights:", (panel_x, panel_y),
            cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2
        )

        # 各方向状态
        y_offset = panel_y + 30
        direction_names = {
            'north_bound': 'North',
            'south_bound': 'South',
            'west_bound': 'West',
            'east_bound': 'East'
        }

        for direction, state in signal_states.items():
            color = (0, 255, 0) if state == 'green' else (0, 0, 255)
            
            # 直行灯
            text = f"{direction_names[direction]}: {state.upper()}"
            cv2.putText(
                frame, text, (panel_x, y_offset),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 2
            )
            
            # 左转灯（显示在同一行）
            left_state = left_turn_signals.get(direction, 'red')
            left_color = (0, 255, 0) if left_state == 'green' else (0, 0, 255)
            left_text = f"L:{left_state[:1].upper()}"
            cv2.putText(
                frame, left_text, (panel_x + 200, y_offset),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, left_color, 2
            )
            
            y_offset += 25

    def _draw_statistics(self, frame, video_time):
        """绘制统计信息（底部）"""
        summary = self.violation_detector.get_violation_summary()
        y_offset = frame.shape[0] - 60

        stats_text = f"Time: {video_time:.1f}s | Violations: {summary['total_violations']} " \
                     f"(Red: {summary['red_light_running']} | Wrong Way: {summary['wrong_way_driving']} | Lane Change: {summary['lane_change_across_solid_line']})"

        # 背景
        cv2.rectangle(frame, (0, y_offset - 35), (frame.shape[1], frame.shape[0]), (0, 0, 0), -1)

        cv2.putText(
            frame, stats_text, (10, y_offset),
            cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2
        )

    def _draw_controls_hint(self, frame):
        """绘制控制提示（左下角）"""
        hints = [
            "Controls: [1-4]Signal [5-6]LeftTurn All [7-0]LeftTurn N/S/W/E [Q]Quit"
        ]

        y_offset = frame.shape[0] - 90

        for hint in hints:
            cv2.putText(
                frame, hint, (10, y_offset),
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1
            )
            y_offset += 20


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description="TrafficMind - 交通违规检测系统（手动控制版本）")
    parser.add_argument("--video", type=str, required=True, help="输入视频路径")
    parser.add_argument("--rois", type=str, default="./data/rois.json", help="ROI配置文件")
    parser.add_argument("--model", type=str, default="yolov8s.pt", help="YOLOv8模型路径（默认yolov8s.pt，速度更快）")
    parser.add_argument("--output", type=str, default=None, help="输出视频路径")
    parser.add_argument("--no-display", action="store_true", help="不实时显示")
    parser.add_argument("--rotation", type=int, default=0, 
                       choices=[0, 90, -90, 180, 270, -270],
                       help="视频旋转角度 (90=顺时针90°, -90=逆时针90°)")
    parser.add_argument("--signal-config", type=str, default=None, help="信号灯时间轴配置文件")
    parser.add_argument("--default-signal", type=str, default="red",  # 改为默认红灯
                       choices=['red', 'green', 'yellow'], help="默认信号灯状态")

    args = parser.parse_args()

    # 创建管道
    pipeline = TrafficViolationPipelineManual(
        rois_path=args.rois,
        model_path=args.model,
        signal_config=args.signal_config,
        default_signal=args.default_signal,
        rotation_angle=args.rotation
    )

    # 处理视频
    pipeline.process_video(
        video_path=args.video,
        output_path=args.output,
        display=not args.no_display
    )


if __name__ == "__main__":
    main()
