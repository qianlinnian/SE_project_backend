"""
图片违规检测测试脚本 - TrafficMind 交通智脑

用法示例:
1. 检测单张图片:
   python scripts/test_image.py --image ./test_images/car1.jpg

2. 检测文件夹中的所有图片:
   python scripts/test_image.py --folder ./test_images

3. 指定信号灯状态:
   python scripts/test_image.py --image ./test.jpg --signals north_bound=red,south_bound=green

4. 只检测闯红灯:
   python scripts/test_image.py --image ./test.jpg --detect red_light

5. 只检测跨实线变道:
   python scripts/test_image.py --image ./test.jpg --detect lane_change
"""

import os
import sys
import argparse
from pathlib import Path
import json

# 添加父目录到 Python 路径，确保可以导入 core 模块
_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_AI_DETECTION_DIR = os.path.dirname(_SCRIPT_DIR)
if _AI_DETECTION_DIR not in sys.path:
    sys.path.insert(0, _AI_DETECTION_DIR)

from core.image_violation_detector import ImageViolationDetector


def parse_signal_states(signals_str):
    """
    解析信号灯状态字符串

    Args:
        signals_str: 格式如 "north_bound=red,south_bound=green,west_bound=green,east_bound=red"

    Returns:
        dict: 信号灯状态字典
    """
    if not signals_str:
        return None

    states = {}
    for item in signals_str.split(','):
        direction, state = item.strip().split('=')
        states[direction.strip()] = state.strip()

    return states


def main():
    parser = argparse.ArgumentParser(description="图片违规检测测试工具")
    parser.add_argument("--image", type=str, help="单张图片路径")
    parser.add_argument("--folder", type=str, help="图片文件夹路径（批量检测）")
    parser.add_argument("--rois", type=str, default="./data/rois.json", help="ROI配置文件")
    parser.add_argument("--model", type=str, default="yolov8s.pt", help="YOLOv8模型路径")
    parser.add_argument("--output", type=str, default="./output/screenshots", help="违规截图保存目录")
    parser.add_argument(
        "--signals",
        type=str,
        help="信号灯状态，格式: north_bound=red,south_bound=green,..."
    )
    parser.add_argument(
        "--detect",
        type=str,
        nargs='+',
        default=['red_light', 'lane_change'],
        choices=['red_light', 'lane_change'],
        help="要检测的违规类型"
    )
    parser.add_argument("--enable-api", action="store_true", help="启用后端API上报")
    parser.add_argument("--export", type=str, help="导出违规记录到JSON文件")
    parser.add_argument("--debug", action="store_true", help="显示调试信息")

    args = parser.parse_args()

    # 检查输入参数
    if not args.image and not args.folder:
        parser.error("必须指定 --image 或 --folder 参数")

    # 解析信号灯状态
    signal_states = parse_signal_states(args.signals)

    # 初始化检测器
    print("=" * 60)
    print("  图片违规检测测试")
    print("=" * 60)

    detector = ImageViolationDetector(
        rois_path=args.rois,
        model_path=args.model,
        screenshot_dir=args.output,
        enable_api=args.enable_api
    )

    print(f"\n检测器初始化成功")
    print(f"📁 ROI配置: {args.rois}")
    print(f"🤖 模型: {args.model}")
    print(f"💾 输出目录: {args.output}")
    print(f"🔍 检测类型: {', '.join(args.detect)}")

    if signal_states:
        print(f"信号灯状态:")
        for direction, state in signal_states.items():
            print(f"    {direction}: {state}")
    else:
        print(f"信号灯状态: 默认所有方向红灯")

    print("=" * 60)

    # 收集要处理的图片
    image_files = []
    if args.image:
        image_files.append(Path(args.image))
    elif args.folder:
        folder = Path(args.folder)
        # 支持常见图片格式
        for ext in ['*.jpg', '*.jpeg', '*.png', '*.bmp']:
            image_files.extend(folder.glob(ext))
            image_files.extend(folder.glob(ext.upper()))

    if not image_files:
        print("未找到任何图片文件")
        return

    print(f"\n📸 找到 {len(image_files)} 张图片")

    # 处理每张图片
    all_results = []
    for image_path in image_files:
        result = detector.process_image(
            str(image_path),
            signal_states=signal_states,
            detect_types=args.detect,
            debug=args.debug
        )
        if result:
            all_results.append(result)

    # 汇总统计
    print("\n" + "=" * 60)
    print("总体统计")
    print("=" * 60)

    total_violations = sum(r['total_violations'] for r in all_results)
    total_red_light = sum(r['red_light_violations'] for r in all_results)
    total_lane_change = sum(r['lane_change_violations'] for r in all_results)

    print(f"  处理图片数: {len(all_results)}")
    print(f"  总违规数: {total_violations}")
    print(f"    - 闯红灯: {total_red_light}")
    print(f"    - 跨实线变道: {total_lane_change}")

    # 导出违规记录
    if args.export:
        detector.export_violations(args.export)
        print(f"\n违规记录已导出到: {args.export}")

    print("=" * 60)
    print("检测完成！")
    print("=" * 60)


if __name__ == "__main__":
    main()
