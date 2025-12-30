import cv2
import numpy as np
import pprint
import os
import json
from datetime import datetime

# ================= 配置 =================
# 获取脚本所在目录，然后定位图片
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
IMAGE_PATH = os.path.join(SCRIPT_DIR, '..', 'data', 'background.png')
OUTPUT_JSON = os.path.join(SCRIPT_DIR, '..', 'data', 'rois2.json')
# =======================================

# 全局变量
current_points = []
current_direction = 'north_bound' # 当前操作的区域：北侧/南侧
current_category = 'stop_line'    # 当前模式：停止线/车道/实线
current_flow = None               # 当前车流方向：in/out
current_lane_from = 0             # 实线左侧车道索引
current_lane_to = 1               # 实线右侧车道索引

# 核心数据结构：lanes 下面的 in 和 out 都是列表 []
saved_data = {
    "solid_lines": [],  # 实线列表
    "north_bound": {    # 北侧
        "stop_line": [],
        "lanes": { "in": [], "out": [] },
        "left_turn_waiting_area": []  # 左转待转区
    },
    "south_bound": {    # 南侧
        "stop_line": [],
        "lanes": { "in": [], "out": [] },
        "left_turn_waiting_area": []  # 左转待转区
    },
    "west_bound": {     # 西侧
        "stop_line": [],
        "lanes": { "in": [], "out": [] },
        "left_turn_waiting_area": []  # 左转待转区
    },
    "east_bound": {     # 东侧
        "stop_line": [],
        "lanes": { "in": [], "out": [] },
        "left_turn_waiting_area": []  # 左转待转区
    }, 
}

def mouse_callback(event, x, y, flags, param):
    global current_points
    if event == cv2.EVENT_LBUTTONDOWN:
        current_points.append((x, y))

def point_to_polygon_distance(point, polygon):
    """计算点到多边形的最小距离"""
    poly = np.array(polygon, dtype=np.int32)
    dist = cv2.pointPolygonTest(poly, point, True)
    return abs(dist)

def get_config(category, flow=None):
    """返回颜色和提示语"""
    if category == 'stop_line':
        return (0, 0, 255), "STOP LINE" # 红色：停止线
    elif category == 'solid_line':
        return (255, 255, 0), "SOLID LINE" # 青色：实线
    elif category == 'left_turn_waiting_area':
        return (0, 165, 255), "LEFT TURN WAITING AREA" # 橙色：左转待转区
    elif category == 'lanes':
        if flow == 'in':
            return (0, 255, 0), "LANE: IN"   # 绿色：驶入路口
        elif flow == 'out':
            return (255, 0, 0), "LANE: OUT" # 蓝色：驶出路口
    return (255, 255, 255), "UNKNOWN"

def main():
    global current_points, current_direction, current_category, current_flow, current_lane_from, current_lane_to, saved_data
    
    img = cv2.imread(IMAGE_PATH)
    if img is None:
        print(f"找不到图片 {IMAGE_PATH}")
        return

    # 尝试加载已有的ROI数据
    if os.path.exists(OUTPUT_JSON):
        try:
            with open(OUTPUT_JSON, 'r', encoding='utf-8') as f:
                loaded_data = json.load(f)
            saved_data = loaded_data
            
            # 确保solid_lines字段存在
            if 'solid_lines' not in saved_data:
                saved_data['solid_lines'] = []
            
            # 确保所有方向都存在
            for direction in ['north_bound', 'south_bound', 'west_bound', 'east_bound']:
                if direction not in saved_data:
                    saved_data[direction] = {
                        "stop_line": [],
                        "lanes": {"in": [], "out": []},
                        "left_turn_waiting_area": []
                    }
                # 确保left_turn_waiting_area字段存在
                if 'left_turn_waiting_area' not in saved_data[direction]:
                    saved_data[direction]['left_turn_waiting_area'] = []
            
            print(f"已加载现有ROI数据: {OUTPUT_JSON}")
            
            # 统计已有数据
            num_solid_lines = len(saved_data.get('solid_lines', []))
            print(f"   - 实线: {num_solid_lines} 条")
            for direction in ['north_bound', 'south_bound', 'west_bound', 'east_bound']:
                num_in = len(saved_data.get(direction, {}).get('lanes', {}).get('in', []))
                num_out = len(saved_data.get(direction, {}).get('lanes', {}).get('out', []))
                num_stop = len(saved_data.get(direction, {}).get('stop_line', []))
                if num_in + num_out + num_stop > 0:
                    print(f"   - {direction}: IN={num_in}, OUT={num_out}, Stop={num_stop}")
        except Exception as e:
            print(f"⚠️ 加载现有数据失败: {e}")
            print("   将使用空白数据开始")
    else:
        print(f"ℹ️ 未找到现有ROI文件，将创建新文件")

    window_name = 'Bi-Directional Labeling'
    cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
    cv2.resizeWindow(window_name, 1280, 720)
    cv2.setMouseCallback(window_name, mouse_callback)

    print("\n=== 双向多车道标记工具 ===")
    print("【N / S / W / E】: 切换区域 (North / South / West / East)")
    print("--- 绘画模式 ---")
    print("【1】: 停止线 (红色)")
    print("【2】: IN 车道 (绿色) - 驶入路口")
    print("【3】: OUT 车道 (蓝色) - 驶出路口")
    print("【4】: 实线 (青色) - 车道分界线 (2个点定义一条线)")
    print("【5】: 左转待转区 (橙色) - 左转车辆等待区域")
    print("")
    print("实线检测原理: 只要车辆穿越实线就算违规，无需标记车道编号")
    print("待转区用途: 标记左转车辆在绿灯时可以进入等待的区域")
    print("--- 操作 ---")
    print("【D】: 保存当前标注")
    print("【Z】: 撤销点")
    print("【C】: 清空当前点")
    print("【Q】: 完成并退出")
    print("====================================\n")

    while True:
        display_img = img.copy()

        # --- 1. 渲染所有已保存的数据 ---
        
        # A. 画实线
        for solid_line in saved_data.get('solid_lines', []):
            pts = solid_line['coordinates']
            if len(pts) >= 2:
                cv2.line(display_img, tuple(pts[0]), tuple(pts[1]), (255, 255, 0), 3)
                # 标记实线名称
                mid_x = (pts[0][0] + pts[1][0]) // 2
                mid_y = (pts[0][1] + pts[1][1]) // 2
                cv2.putText(display_img, solid_line['name'], (mid_x, mid_y), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 0), 2)
        
        for direction, data in saved_data.items():
            if direction == 'solid_lines':
                continue
                
            # B. 画停止线
            for poly in data['stop_line']:
                pts = np.array([poly], np.int32)
                cv2.polylines(display_img, [pts], True, (0, 0, 255), 2)
                # 标记中心点
                M = cv2.moments(pts)
                if M["m00"] != 0:
                    cX, cY = int(M["m10"]/M["m00"]), int(M["m01"]/M["m00"])
                    cv2.putText(display_img, f"{direction[0]}-Stop", (cX-20, cY), 0, 0.5, (255,255,255), 1)

            # C. 画车道
            for flow_type, polygons in data['lanes'].items():
                color, _ = get_config('lanes', flow_type)
                
                # 遍历列表里的每一条车道
                for i, poly in enumerate(polygons):
                    pts = np.array([poly], np.int32)
                    cv2.polylines(display_img, [pts], True, color, 2)
                    
                    # 标记是第几条道
                    M = cv2.moments(pts)
                    if M["m00"] != 0:
                        cX, cY = int(M["m10"]/M["m00"]), int(M["m01"]/M["m00"])
                        label = f"{direction[0]}-{flow_type}-{i}"
                        cv2.putText(display_img, label, (cX-30, cY), 0, 0.5, (255,255,255), 1)
            
            # D. 画左转待转区
            if 'left_turn_waiting_area' in data:
                for i, poly in enumerate(data['left_turn_waiting_area']):
                    pts = np.array([poly], np.int32)
                    cv2.polylines(display_img, [pts], True, (0, 165, 255), 2)
                    # 填充半透明效果
                    overlay = display_img.copy()
                    cv2.fillPoly(overlay, [pts], (0, 165, 255))
                    cv2.addWeighted(overlay, 0.3, display_img, 0.7, 0, display_img)
                    
                    # 标记
                    M = cv2.moments(pts)
                    if M["m00"] != 0:
                        cX, cY = int(M["m10"]/M["m00"]), int(M["m01"]/M["m00"])
                        label = f"{direction[0]}-LeftWait-{i}"
                        cv2.putText(display_img, label, (cX-40, cY), 0, 0.5, (255,255,255), 2)

        # --- 2. 渲染当前正在画的 ---
        cur_color, cur_name = get_config(current_category, current_flow)
        if len(current_points) > 0:
            for pt in current_points:
                cv2.circle(display_img, pt, 5, cur_color, -1)
            
            if current_category == 'solid_line' and len(current_points) >= 2:
                # 实线只需要2个点，画直线
                cv2.line(display_img, current_points[0], current_points[1], cur_color, 2)
            elif len(current_points) > 1:
                cv2.polylines(display_img, [np.array(current_points)], False, cur_color, 1)

        # --- 3. UI 状态栏 ---
        cv2.rectangle(display_img, (0, 0), (650, 120), (0, 0, 0), -1)
         
        if current_direction == 'north_bound':
            dir_text = "NORTH AREA"
        elif current_direction == 'south_bound':
            dir_text = "SOUTH AREA"
        elif current_direction == 'west_bound':
            dir_text = "WEST AREA"
        elif current_direction == 'east_bound':
            dir_text = "EAST AREA"
        
        cv2.putText(display_img, f"AREA: {dir_text} [N/S/W/E]", (10, 30), 0, 0.7, (255, 255, 255), 2)
        cv2.putText(display_img, f"MODE: {cur_name}", (10, 60), 0, 0.7, cur_color, 2)
        
        if current_category == 'solid_line':
            num_lines = len([sl for sl in saved_data.get('solid_lines', []) 
                           if sl['direction'] == current_direction])
            cv2.putText(display_img, f"{current_direction}: {num_lines} solid lines", 
                       (10, 90), 0, 0.6, (255, 255, 0), 1)
        
        cv2.putText(display_img, "Keys: 1=Stop, 2=In, 3=Out, 4=SolidLine, 5=LeftWait | D=Save Z=Undo C=Clear", 
                   (10, 115), 0, 0.5, (200, 200, 200), 1)

        cv2.imshow(window_name, display_img)
        
        key = cv2.waitKey(20) & 0xFF

        # --- 逻辑控制 ---
        if key == ord('n'): current_direction = 'north_bound'
        elif key == ord('s'): current_direction = 'south_bound'
        elif key == ord('w'): current_direction = 'west_bound'
        elif key == ord('e'): current_direction = 'east_bound'
        
        elif key == ord('1'): 
            current_category = 'stop_line'
            current_flow = None
        elif key == ord('2'):
            current_category = 'lanes'
            current_flow = 'in'
        elif key == ord('3'):
            current_category = 'lanes'
            current_flow = 'out'
        elif key == ord('4'):
            current_category = 'solid_line'
            current_flow = None
        elif key == ord('5'):
            current_category = 'left_turn_waiting_area'
            current_flow = None

        elif key == ord('z'): # 撤销
            if current_points: current_points.pop()
        
        elif key == ord('c'): # 清空
            current_points = []
            print("🗑️ 已清空当前点")

        elif key == ord('d'): # 保存
            if current_category == 'solid_line':
                # 实线只需要2个点
                if len(current_points) == 2:
                    # 简化：直接保存实线，不需要检测车道关系
                    num_lines = len([sl for sl in saved_data['solid_lines'] 
                                    if sl['direction'] == current_direction])
                    
                    solid_line_name = f"{current_direction}_line_{num_lines}"
                    solid_line_data = {
                        'name': solid_line_name,
                        'direction': current_direction,
                        'coordinates': current_points.copy()
                    }
                    saved_data['solid_lines'].append(solid_line_data)
                    print(f"已保存实线: {solid_line_name}")
                    current_points = []
                else:
                    print(f"⚠️ 实线需要恰好2个点 (当前: {len(current_points)})")
            
            elif len(current_points) >= 3:
                if current_category == 'stop_line':
                    saved_data[current_direction]['stop_line'].append(current_points.copy())
                    print(f"Saved: {current_direction} -> stop_line")
                elif current_category == 'left_turn_waiting_area':
                    saved_data[current_direction]['left_turn_waiting_area'].append(current_points.copy())
                    print(f"Saved: {current_direction} -> left_turn_waiting_area")
                else:
                    saved_data[current_direction]['lanes'][current_flow].append(current_points.copy())
                    print(f"Saved: {current_direction} -> {current_category} -> {current_flow}")
                
                current_points = []
            else:
                print("⚠️ 点数不足 (停止线/车道/待转区至少需要3个点)")

        elif key == ord('q'):
            break

    cv2.destroyAllWindows()
    
    # 保存为JSON
    try:
        with open(OUTPUT_JSON, 'w', encoding='utf-8') as f:
            json.dump(saved_data, f, indent=2, ensure_ascii=False)
        print(f"\n已保存到: {OUTPUT_JSON}")
    except Exception as e:
        print(f"\n保存失败: {e}")
    
    # 控制台打印
    print("\n" + "="*50)
    print("ROIS = ", end="")
    pprint.pprint(saved_data, width=120)
    print("="*50)

if __name__ == "__main__":
    main()