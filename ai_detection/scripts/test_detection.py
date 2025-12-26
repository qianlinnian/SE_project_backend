"""
AI检测功能测试脚本
用于测试图片和视频检测功能，不依赖前端
"""

import cv2
import os
import sys
from pathlib import Path

# 添加项目路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from ai_detection.core.image_violation_detector import ImageViolationDetector


def test_image_detection(image_path: str):
    """
    测试图片检测功能

    Args:
        image_path: 图片路径
    """
    print("=" * 60)
    print(f"测试图片检测: {image_path}")
    print("=" * 60)

    # 检查图片是否存在
    if not os.path.exists(image_path):
        print(f"❌ 错误: 图片不存在: {image_path}")
        return

    # 读取图片
    image = cv2.imread(image_path)
    if image is None:
        print(f"❌ 错误: 无法读取图片: {image_path}")
        return

    print(f"✅ 图片读取成功: {image.shape}")

    # 初始化检测器
    print("\n正在初始化检测器...")
    detector = ImageViolationDetector()
    print("✅ 检测器初始化成功")

    # 执行检测
    print("\n正在执行检测...")
    result = detector.process_image(image_path)

    # 打印结果
    print("\n" + "=" * 60)
    print("检测结果:")
    print("=" * 60)
    print(f"成功: {result.get('success', False)}")
    print(f"违规总数: {result.get('total_violations', 0)}")

    violations = result.get('violations', [])
    if violations:
        print(f"\n检测到 {len(violations)} 个违规:")
        for i, v in enumerate(violations, 1):
            print(f"\n违规 #{i}:")
            print(f"  - ID: {v.get('id', 'N/A')}")
            print(f"  - 类型: {v.get('type', 'N/A')}")
            print(f"  - 方向: {v.get('direction', 'N/A')}")
            print(f"  - 置信度: {v.get('confidence', 0):.2f}")
            print(f"  - Track ID: {v.get('track_id', 'N/A')}")
            print(f"  - 边界框: {v.get('bbox', 'N/A')}")
    else:
        print("\n✅ 未检测到违规")

    # 保存标注图片
    annotated_image = result.get('annotated_image')
    if annotated_image is not None:
        output_dir = Path(__file__).parent / "test_output"
        output_dir.mkdir(exist_ok=True)

        output_path = output_dir / f"annotated_{Path(image_path).name}"
        cv2.imwrite(str(output_path), annotated_image)
        print(f"\n✅ 标注图片已保存: {output_path}")
        print(f"   使用图片查看器打开: {output_path.absolute()}")
    else:
        print("\n⚠️ 警告: 未生成标注图片")

    print("\n" + "=" * 60)


def test_image_detection_data(image_path: str):
    """
    测试使用 process_image_data 的图片检测

    Args:
        image_path: 图片路径
    """
    print("=" * 60)
    print(f"测试图片检测 (process_image_data): {image_path}")
    print("=" * 60)

    # 检查图片是否存在
    if not os.path.exists(image_path):
        print(f"❌ 错误: 图片不存在: {image_path}")
        return

    # 读取图片
    image = cv2.imread(image_path)
    if image is None:
        print(f"❌ 错误: 无法读取图片: {image_path}")
        return

    print(f"✅ 图片读取成功: {image.shape}")

    # 初始化检测器
    print("\n正在初始化检测器...")
    detector = ImageViolationDetector()
    print("✅ 检测器初始化成功")

    # 执行检测
    print("\n正在执行检测...")
    result = detector.process_image_data(image)

    # 打印结果
    print("\n" + "=" * 60)
    print("检测结果:")
    print("=" * 60)
    print(f"成功: {result.get('success', False)}")
    print(f"违规总数: {result.get('total_violations', 0)}")

    violations = result.get('violations', [])
    if violations:
        print(f"\n检测到 {len(violations)} 个违规:")
        for i, v in enumerate(violations, 1):
            print(f"\n违规 #{i}:")
            print(f"  - ID: {v.get('id', 'N/A')}")
            print(f"  - 类型: {v.get('type', 'N/A')}")
            print(f"  - 方向: {v.get('direction', 'N/A')}")
            print(f"  - 置信度: {v.get('confidence', 0):.2f}")
            print(f"  - Track ID: {v.get('track_id', 'N/A')}")
    else:
        print("\n✅ 未检测到违规")

    # 保存标注图片
    annotated_image = result.get('annotated_image')
    if annotated_image is not None:
        output_dir = Path(__file__).parent / "test_output"
        output_dir.mkdir(exist_ok=True)

        output_path = output_dir / f"annotated_data_{Path(image_path).name}"
        cv2.imwrite(str(output_path), annotated_image)
        print(f"\n✅ 标注图片已保存: {output_path}")
        print(f"   使用图片查看器打开: {output_path.absolute()}")
    else:
        print("\n⚠️ 警告: 未生成标注图片")

    print("\n" + "=" * 60)


def list_test_images():
    """列出可用的测试图片"""
    print("\n" + "=" * 60)
    print("可用的测试图片:")
    print("=" * 60)

    # 违规图片目录
    violations_dir = Path(__file__).parent / "violations_images"

    if violations_dir.exists():
        images = list(violations_dir.glob("*.png")) + list(violations_dir.glob("*.jpg"))
        if images:
            for i, img in enumerate(images, 1):
                print(f"{i}. {img.name}")
            return images
        else:
            print("未找到图片文件")
            return []
    else:
        print(f"目录不存在: {violations_dir}")
        return []


def main():
    """主函数"""
    print("\n" + "=" * 60)
    print("AI检测功能测试工具")
    print("=" * 60)

    # 检查是否提供了图片路径参数
    if len(sys.argv) > 1:
        image_path = sys.argv[1]
        print(f"\n使用提供的图片: {image_path}")
        test_image_detection(image_path)
        print("\n" + "-" * 60)
        test_image_detection_data(image_path)
    else:
        # 列出可用的测试图片
        images = list_test_images()

        if images:
            print("\n请选择一张图片进行测试:")
            try:
                choice = int(input("输入序号 (1-{}): ".format(len(images))))
                if 1 <= choice <= len(images):
                    selected_image = str(images[choice - 1])
                    print(f"\n选择的图片: {selected_image}")

                    # 测试两种方法
                    test_image_detection(selected_image)
                    print("\n" + "-" * 60)
                    test_image_detection_data(selected_image)
                else:
                    print("无效的选择")
            except ValueError:
                print("无效的输入")
            except KeyboardInterrupt:
                print("\n\n测试已取消")
        else:
            print("\n使用方法:")
            print("  python test_detection.py <图片路径>")
            print("\n示例:")
            print("  python test_detection.py violations_images/car_1_cross.png")


if __name__ == "__main__":
    main()
