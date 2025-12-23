"""
视频旋转工具 - 用于修正旋转的视频

使用场景：
- 视频拍摄时角度不正
- 手机竖屏拍摄的视频
- 需要旋转视频才能正确检测车辆
"""

import cv2
import argparse
import numpy as np


def rotate_video(input_path, output_path, rotation_angle=90, auto_detect=False):
    """
    旋转视频
    
    Args:
        input_path: 输入视频路径
        output_path: 输出视频路径
        rotation_angle: 旋转角度 (90, 180, 270, -90)
        auto_detect: 是否自动检测并修正旋转（根据元数据）
    """
    print(f"📹 正在旋转视频: {input_path}")
    print(f"   旋转角度: {rotation_angle}°")
    
    # 打开视频
    cap = cv2.VideoCapture(input_path)
    if not cap.isOpened():
        print(f"❌ 无法打开视频: {input_path}")
        return
    
    # 获取视频属性
    fps = int(cap.get(cv2.CAP_PROP_FPS))
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    
    # 确定旋转后的尺寸
    if rotation_angle in [90, -90, 270]:
        # 90度或270度旋转，宽高互换
        out_width, out_height = height, width
    else:
        # 180度旋转，尺寸不变
        out_width, out_height = width, height
    
    # 创建视频写入器
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    out = cv2.VideoWriter(output_path, fourcc, fps, (out_width, out_height))
    
    print(f"   原始尺寸: {width}x{height}")
    print(f"   旋转后尺寸: {out_width}x{out_height}")
    print(f"   帧率: {fps} FPS")
    print(f"   总帧数: {total_frames}")
    
    # 确定OpenCV旋转代码
    rotate_code = None
    if rotation_angle == 90 or rotation_angle == -270:
        rotate_code = cv2.ROTATE_90_CLOCKWISE
    elif rotation_angle == -90 or rotation_angle == 270:
        rotate_code = cv2.ROTATE_90_COUNTERCLOCKWISE
    elif rotation_angle == 180 or rotation_angle == -180:
        rotate_code = cv2.ROTATE_180
    else:
        print(f"⚠️ 不支持的旋转角度: {rotation_angle}")
        print("   支持的角度: 90, -90, 180, 270")
        cap.release()
        return
    
    # 处理每一帧
    frame_count = 0
    print("\n🎬 开始旋转...")
    
    while True:
        ret, frame = cap.read()
        if not ret:
            break
        
        # 旋转帧
        rotated_frame = cv2.rotate(frame, rotate_code)
        
        # 写入输出视频
        out.write(rotated_frame)
        
        frame_count += 1
        if frame_count % 30 == 0:
            progress = (frame_count / total_frames) * 100
            print(f"\r  进度: {frame_count}/{total_frames} ({progress:.1f}%)", end='')
    
    print(f"\r  进度: {frame_count}/{total_frames} (100.0%)  ")
    
    # 释放资源
    cap.release()
    out.release()
    
    print(f"✅ 旋转完成！保存到: {output_path}")
    print(f"   处理了 {frame_count} 帧")


def rotate_frame(frame, angle):
    """
    旋转单个帧（用于实时处理）
    
    Args:
        frame: 输入帧
        angle: 旋转角度 (90, -90, 180, 270)
    
    Returns:
        旋转后的帧
    """
    if angle == 90 or angle == -270:
        return cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
    elif angle == -90 or angle == 270:
        return cv2.rotate(frame, cv2.ROTATE_90_COUNTERCLOCKWISE)
    elif angle == 180 or angle == -180:
        return cv2.rotate(frame, cv2.ROTATE_180)
    else:
        return frame


def main():
    parser = argparse.ArgumentParser(description="视频旋转工具")
    parser.add_argument("--input", type=str, required=True, help="输入视频路径")
    parser.add_argument("--output", type=str, required=True, help="输出视频路径")
    parser.add_argument("--angle", type=int, default=90, 
                       choices=[90, -90, 180, 270, -270],
                       help="旋转角度 (90=顺时针90°, -90=逆时针90°, 180=翻转180°)")
    
    args = parser.parse_args()
    
    rotate_video(args.input, args.output, args.angle)


if __name__ == "__main__":
    main()
