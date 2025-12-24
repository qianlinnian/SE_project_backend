"""
简单测试 YOLOv8 是否能正常检测
"""
import cv2
from ultralytics import YOLO

# 读取图片
img_path = "./data/car_1_red.png"
print(f"读取图片: {img_path}")
img = cv2.imread(img_path)

if img is None:
    print("无法读取图片!")
    exit(1)

print(f"图片读取成功: {img.shape}")

# 加载模型
print("\n加载 YOLOv8 模型...")
model = YOLO("yolov8s.pt")
print("模型加载成功")

# 测试不同的置信度阈值
for conf in [0.1, 0.2, 0.3, 0.4, 0.5]:
    print(f"\n{'='*60}")
    print(f"置信度阈值: {conf}")
    print(f"{'='*60}")

    results = model(img, conf=conf, verbose=False)

    for result in results:
        boxes = result.boxes
        print(f"检测到 {len(boxes)} 个目标")

        if len(boxes) > 0:
            for idx, box in enumerate(boxes):
                cls = int(box.cls[0])
                confidence = float(box.conf[0])
                x1, y1, x2, y2 = box.xyxy[0].cpu().numpy()
                print(f"  [{idx}] 类别: {cls}, 置信度: {confidence:.3f}, bbox: ({int(x1)},{int(y1)},{int(x2)},{int(y2)})")

print(f"\n{'='*60}")
print("测试完成!")
print(f"{'='*60}")
