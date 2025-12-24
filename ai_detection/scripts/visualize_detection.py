"""
可视化 ROI 和车辆检测结果
"""
import cv2
import json
import numpy as np
from ultralytics import YOLO

# 读取图片
img_path = "./data/car_1_red.png"
img = cv2.imread(img_path)

# 加载 ROI
with open("./data/rois.json") as f:
    rois = json.load(f)

# 加载模型并检测车辆
model = YOLO("yolov8s.pt")
results = model(img, conf=0.15, verbose=False)

# 绘制车辆
vehicle_classes = [2, 3, 5, 7]
for result in results:
    for box in result.boxes:
        cls = int(box.cls[0])
        if cls in vehicle_classes:
            x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
            x1, y1, x2, y2 = int(x1), int(y1), int(x2), int(y2)

            # 绘制车辆框（绿色）
            cv2.rectangle(img, (x1, y1), (x2, y2), (0, 255, 0), 3)

            # 绘制底部中心点
            bottom_center = (int((x1 + x2) / 2), y2)
            cv2.circle(img, bottom_center, 8, (0, 255, 255), -1)
            cv2.putText(img, f"Car ({bottom_center[0]},{bottom_center[1]})",
                       (bottom_center[0] + 10, bottom_center[1]),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 255), 2)

# 绘制停止线（红色）
for direction in ['north_bound', 'south_bound', 'west_bound', 'east_bound']:
    stop_lines = rois[direction]['stop_line']
    for stop_line_poly in stop_lines:
        pts = np.array(stop_line_poly, dtype=np.int32)
        cv2.polylines(img, [pts], True, (0, 0, 255), 3)

        # 标注方向
        center_x = int(np.mean([p[0] for p in stop_line_poly]))
        center_y = int(np.mean([p[1] for p in stop_line_poly]))
        cv2.putText(img, direction.replace('_bound', ''),
                   (center_x, center_y),
                   cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 255), 2)

# 保存结果
output_path = "./data/detection_visualization.jpg"
cv2.imwrite(output_path, img)
print(f"可视化结果已保存到: {output_path}")
print(f"\n说明:")
print("  - 绿色框: 检测到的车辆")
print("  - 黄色点: 车辆底部中心点")
print("  - 红色框: 停止线区域")
