"""
测试 Flask API - 上传图片并获取检测结果
"""

import requests
import base64
import json
from pathlib import Path

API_URL = "http://localhost:5000"


def test_health():
    """测试健康检查接口"""
    print("\n" + "=" * 60)
    print("1. 测试健康检查")
    print("=" * 60)

    response = requests.get(f"{API_URL}/health")
    print(f"状态码: {response.status_code}")
    print(f"响应: {json.dumps(response.json(), indent=2, ensure_ascii=False)}")


def test_detect_image_multipart(image_path):
    """测试图片检测接口（multipart/form-data）"""
    print("\n" + "=" * 60)
    print("2. 测试图片检测接口（Multipart）")
    print("=" * 60)
    print(f"上传图片: {image_path}")

    # 准备文件
    with open(image_path, 'rb') as f:
        files = {'image': (Path(image_path).name, f, 'image/jpeg')}

        # 准备表单数据
        data = {
            'detect_types': 'red_light,lane_change',
            'signals': json.dumps({
                'north_bound': 'red',
                'south_bound': 'red',
                'west_bound': 'red',
                'east_bound': 'red'
            })
        }

        response = requests.post(
            f"{API_URL}/detect-image",
            files=files,
            data=data
        )

    print(f"\n状态码: {response.status_code}")

    if response.status_code == 200:
        result = response.json()
        print(f"\n检测结果:")
        print(f"  - 成功: {result.get('success')}")
        print(f"  - 图片名称: {result.get('image_name')}")
        print(f"  - 图片尺寸: {result.get('image_size')}")
        print(f"  - 总违规数: {result.get('total_violations')}")
        print(f"  - 闯红灯: {result.get('red_light_violations')}")
        print(f"  - 压实线: {result.get('lane_change_violations')}")

        violations = result.get('violations', [])
        if violations:
            print(f"\n违规详情:")
            for i, v in enumerate(violations, 1):
                print(f"  {i}. 类型: {v['type']}")
                print(f"     方向: {v.get('direction', 'N/A')}")
                print(f"     置信度: {v.get('confidence', 0):.2f}")
                print(f"     位置: {v.get('location')}")
                screenshot = v.get('screenshot', 'N/A')
                print(f"     截图: {screenshot}")
                if 'screenshot_base64' in v:
                    print(f"     截图Base64长度: {len(v['screenshot_base64'])} 字节")

        # 保存标注图片
        if 'annotated_image' in result:
            annotated_base64 = result['annotated_image']
            output_path = f"./data/annotated_{Path(image_path).name}"
            with open(output_path, 'wb') as f:
                f.write(base64.b64decode(annotated_base64))
            print(f"\n✅ 标注图片已保存: {output_path}")

    else:
        print(f"错误: {response.text}")


def test_detect_image_base64(image_path):
    """测试图片检测接口（Base64）"""
    print("\n" + "=" * 60)
    print("3. 测试图片检测接口（Base64）")
    print("=" * 60)
    print(f"上传图片: {image_path}")

    # 读取图片并转为 Base64
    with open(image_path, 'rb') as f:
        image_base64 = base64.b64encode(f.read()).decode('utf-8')

    # 准备请求数据
    data = {
        'image': image_base64,
        'detect_types': ['red_light', 'lane_change'],
        'signals': {
            'north_bound': 'red',
            'south_bound': 'red',
            'west_bound': 'red',
            'east_bound': 'red'
        }
    }

    response = requests.post(
        f"{API_URL}/detect-image-base64",
        json=data
    )

    print(f"\n状态码: {response.status_code}")

    if response.status_code == 200:
        result = response.json()
        print(f"\n检测结果:")
        print(f"  - 成功: {result.get('success')}")
        print(f"  - 总违规数: {result.get('total_violations')}")
        print(f"  - 闯红灯: {result.get('red_light_violations')}")
        print(f"  - 压实线: {result.get('lane_change_violations')}")
    else:
        print(f"错误: {response.text}")


if __name__ == "__main__":
    print("=" * 60)
    print("  Flask API 测试")
    print("=" * 60)

    try:
        # 1. 健康检查
        test_health()

        # 2. 测试图片检测（Multipart）
        test_detect_image_multipart("./data/car_1_red.png")

        # 3. 测试图片检测（Base64）
        # test_detect_image_base64("./data/car_1_cross.png")

        print("\n" + "=" * 60)
        print("✅ 测试完成")
        print("=" * 60)

    except requests.exceptions.ConnectionError:
        print("\n❌ 错误: 无法连接到 Flask API (http://localhost:5000)")
        print("请确保 Flask 服务已启动: python api/detection_api.py")
    except Exception as e:
        print(f"\n❌ 错误: {type(e).__name__}: {e}")
