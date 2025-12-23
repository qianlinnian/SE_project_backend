"""
ROI可视化工具 - 渲染和显示ROI配置

功能：
1. 加载rois.json配置文件
2. 在背景图上绘制所有ROI区域
3. 支持不同类型ROI的颜色区分
4. 保存可视化结果

使用方法：
    python Utility/roi_visualizer.py
    python Utility/roi_visualizer.py --rois data/rois.json --bg data/background.png
"""

import cv2
import numpy as np
import json
import argparse
from pathlib import Path


class ROIVisualizer:
    """ROI可视化器"""
    
    # 颜色配置 (BGR格式)
    COLORS = {
        'stop_line': (0, 0, 255),          # 红色 - 停止线
        'lane_in': (0, 255, 0),            # 绿色 - 驶入车道
        'lane_out': (255, 0, 0),           # 蓝色 - 驶出车道
        'solid_line': (255, 255, 0),       # 青色 - 实线
        'waiting_area': (0, 165, 255),     # 橙色 - 左转待转区
    }
    
    def __init__(self, rois_path: str, background_path: str = None):
        """
        初始化ROI可视化器
        
        Args:
            rois_path: ROI配置文件路径
            background_path: 背景图片路径（可选）
        """
        self.rois_path = Path(rois_path)
        self.background_path = Path(background_path) if background_path else None
        
        # 加载ROI数据
        with open(self.rois_path, 'r', encoding='utf-8') as f:
            self.rois = json.load(f)
        
        print(f"✅ 已加载ROI配置: {self.rois_path}")
        
    def load_background(self):
        """
        加载背景图片
        
        Returns:
            背景图片（numpy数组），如果无法加载则返回空白图片
        """
        if self.background_path and self.background_path.exists():
            img = cv2.imread(str(self.background_path))
            if img is not None:
                print(f"✅ 已加载背景图: {self.background_path}")
                return img
            else:
                print(f"⚠️ 无法加载背景图: {self.background_path}")
        
        # 创建空白图片 (1920x1080, 黑色背景)
        print("📝 使用空白背景")
        return np.zeros((1080, 1920, 3), dtype=np.uint8)
    
    def draw_solid_lines(self, frame):
        """
        绘制实线
        
        Args:
            frame: 图像帧
        """
        if 'solid_lines' not in self.rois:
            return
        
        for solid_line in self.rois['solid_lines']:
            pts = solid_line['coordinates']
            if len(pts) >= 2:
                cv2.line(frame, tuple(pts[0]), tuple(pts[1]), 
                        self.COLORS['solid_line'], 3)
                
                # 标记实线名称
                mid_x = (pts[0][0] + pts[1][0]) // 2
                mid_y = (pts[0][1] + pts[1][1]) // 2
                cv2.putText(frame, solid_line.get('name', 'Line'), 
                          (mid_x, mid_y), cv2.FONT_HERSHEY_SIMPLEX, 
                          0.5, self.COLORS['solid_line'], 2)
    
    def draw_stop_lines(self, frame, direction, data):
        """
        绘制停止线
        
        Args:
            frame: 图像帧
            direction: 方向名称
            data: 该方向的数据
        """
        for stop_line in data.get('stop_line', []):
            pts = np.array(stop_line, dtype=np.int32)
            cv2.polylines(frame, [pts], True, self.COLORS['stop_line'], 3)
            
            # 标记中心点
            M = cv2.moments(pts)
            if M["m00"] != 0:
                cX, cY = int(M["m10"]/M["m00"]), int(M["m01"]/M["m00"])
                label = f"{direction[0]}-Stop"
                cv2.putText(frame, label, (cX-20, cY), 
                          cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 2)
    
    def draw_lanes(self, frame, direction, data, overlay):
        """
        绘制车道（使用半透明填充）
        
        Args:
            frame: 图像帧
            direction: 方向名称
            data: 该方向的数据
            overlay: 覆盖层（用于半透明效果）
        """
        # 绘制IN车道（绿色）
        for i, lane in enumerate(data.get('lanes', {}).get('in', [])):
            pts = np.array(lane, dtype=np.int32)
            cv2.fillPoly(overlay, [pts], self.COLORS['lane_in'])
            
            # 标记
            M = cv2.moments(pts)
            if M["m00"] != 0:
                cX, cY = int(M["m10"]/M["m00"]), int(M["m01"]/M["m00"])
                label = f"{direction[0]}-IN-{i}"
                cv2.putText(frame, label, (cX-30, cY), 
                          cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
        
        # 绘制OUT车道（蓝色）
        for i, lane in enumerate(data.get('lanes', {}).get('out', [])):
            pts = np.array(lane, dtype=np.int32)
            cv2.fillPoly(overlay, [pts], self.COLORS['lane_out'])
            
            # 标记
            M = cv2.moments(pts)
            if M["m00"] != 0:
                cX, cY = int(M["m10"]/M["m00"]), int(M["m01"]/M["m00"])
                label = f"{direction[0]}-OUT-{i}"
                cv2.putText(frame, label, (cX-30, cY), 
                          cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
    
    def draw_waiting_areas(self, frame, direction, data):
        """
        绘制左转待转区
        
        Args:
            frame: 图像帧
            direction: 方向名称
            data: 该方向的数据
        """
        if 'left_turn_waiting_area' not in data:
            return
        
        for i, area_poly in enumerate(data['left_turn_waiting_area']):
            pts = np.array(area_poly, dtype=np.int32)
            
            # 绘制边框
            cv2.polylines(frame, [pts], True, self.COLORS['waiting_area'], 2)
            
            # 半透明填充
            overlay = frame.copy()
            cv2.fillPoly(overlay, [pts], self.COLORS['waiting_area'])
            cv2.addWeighted(overlay, 0.3, frame, 0.7, 0, frame)
            
            # 标记
            M = cv2.moments(pts)
            if M["m00"] != 0:
                cX, cY = int(M["m10"]/M["m00"]), int(M["m01"]/M["m00"])
                label = f"{direction[0]}-Wait-{i}"
                cv2.putText(frame, label, (cX-40, cY), 
                          cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 2)
    
    def visualize(self, alpha=0.4, show_labels=True):
        """
        生成ROI可视化图像
        
        Args:
            alpha: 透明度 (0.0-1.0)
            show_labels: 是否显示标签
            
        Returns:
            可视化结果图像
        """
        # 加载背景
        img = self.load_background()
        overlay = img.copy()
        
        # 1. 绘制实线
        self.draw_solid_lines(img)
        
        # 2. 遍历所有方向
        for direction, data in self.rois.items():
            # 跳过solid_lines
            if direction == 'solid_lines':
                continue
            
            # 绘制停止线
            self.draw_stop_lines(img, direction, data)
            
            # 绘制车道（半透明）
            self.draw_lanes(img, direction, data, overlay)
            
            # 绘制左转待转区
            self.draw_waiting_areas(img, direction, data)
        
        # 混合叠加层
        result = cv2.addWeighted(img, 1-alpha, overlay, alpha, 0)
        
        # 添加图例
        self._draw_legend(result)
        
        return result
    
    def _draw_legend(self, frame):
        """
        在图像上绘制图例
        
        Args:
            frame: 图像帧
        """
        legend_x, legend_y = 10, 10
        line_height = 30
        
        # 背景
        cv2.rectangle(frame, (legend_x, legend_y), 
                     (legend_x + 250, legend_y + 180), 
                     (0, 0, 0), -1)
        cv2.rectangle(frame, (legend_x, legend_y), 
                     (legend_x + 250, legend_y + 180), 
                     (255, 255, 255), 2)
        
        # 标题
        y_offset = legend_y + 25
        cv2.putText(frame, "ROI Legend", (legend_x + 10, y_offset), 
                   cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)
        
        # 图例项
        legends = [
            ("Stop Line", self.COLORS['stop_line']),
            ("Lane IN", self.COLORS['lane_in']),
            ("Lane OUT", self.COLORS['lane_out']),
            ("Solid Line", self.COLORS['solid_line']),
            ("Waiting Area", self.COLORS['waiting_area']),
        ]
        
        y_offset += line_height
        for text, color in legends:
            # 颜色块
            cv2.rectangle(frame, 
                         (legend_x + 10, y_offset - 10), 
                         (legend_x + 30, y_offset + 5), 
                         color, -1)
            
            # 文字
            cv2.putText(frame, text, (legend_x + 40, y_offset), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
            
            y_offset += line_height
    
    def save(self, output_path: str, alpha=0.4):
        """
        保存可视化结果
        
        Args:
            output_path: 输出文件路径
            alpha: 透明度
        """
        result = self.visualize(alpha=alpha)
        cv2.imwrite(output_path, result)
        print(f"✅ ROI可视化已保存: {output_path}")
        return result
    
    def show(self, alpha=0.4, window_name="ROI Visualization"):
        """
        显示可视化结果
        
        Args:
            alpha: 透明度
            window_name: 窗口名称
        """
        result = self.visualize(alpha=alpha)
        
        cv2.imshow(window_name, result)
        print("📺 显示ROI可视化（按任意键关闭）")
        cv2.waitKey(0)
        cv2.destroyAllWindows()
        
        return result


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='ROI可视化工具')
    parser.add_argument('--rois', type=str, default='data/rois.json',
                       help='ROI配置文件路径')
    parser.add_argument('--bg', '--background', type=str, default='data/background.png',
                       help='背景图片路径')
    parser.add_argument('--output', type=str, default='data/roi_visualization.png',
                       help='输出图片路径')
    parser.add_argument('--alpha', type=float, default=0.4,
                       help='透明度 (0.0-1.0)')
    parser.add_argument('--no-show', action='store_true',
                       help='不显示图像，仅保存')
    
    args = parser.parse_args()
    
    print("="*60)
    print("🎨 ROI可视化工具")
    print("="*60)
    
    # 创建可视化器
    visualizer = ROIVisualizer(
        rois_path=args.rois,
        background_path=args.bg
    )
    
    # 保存结果
    result = visualizer.save(args.output, alpha=args.alpha)
    
    # 显示结果
    if not args.no_show:
        visualizer.show(alpha=args.alpha)
    
    print("="*60)
    print("✅ 完成！")
    print("="*60)


if __name__ == "__main__":
    main()
