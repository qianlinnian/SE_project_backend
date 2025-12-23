"""
后端API客户端 - TrafficMind 交通智脑
用于违规检测模块与后端API的通信
"""

import requests
from typing import Dict, Optional
from datetime import datetime


class BackendAPIClient:
    """后端API客户端"""

    def __init__(self, base_url: str = "http://localhost:8081/api"):
        """
        初始化API客户端

        Args:
            base_url: 后端API基础URL，默认为本地开发环境
        """
        self.base_url = base_url
        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        })

    def report_violation(self, violation_data: Dict) -> Optional[int]:
        """
        上报违章到后端

        Args:
            violation_data: 违章数据字典，必须包含:
                - intersectionId: int - 路口ID
                - direction: str - 方向 (EAST/SOUTH/WEST/NORTH)
                - turnType: str - 转弯类型 (STRAIGHT/LEFT_TURN/RIGHT_TURN)
                - plateNumber: str - 车牌号
                - violationType: str - 违章类型 (RED_LIGHT/WRONG_WAY/CROSS_SOLID_LINE/ILLEGAL_TURN)
                - imageUrl: str - 图片URL
                - aiConfidence: float - AI置信度 (0-1)
                - occurredAt: str - 发生时间 (ISO 8601格式)

        Returns:
            违章记录ID（成功时），失败返回None
        """
        url = f"{self.base_url}/violations/report"
        try:
            response = self.session.post(url, json=violation_data, timeout=10)

            if response.status_code == 200:
                result = response.json()
                if result.get('code') == 200:
                    violation_id = result.get('data', {}).get('id')
                    print(f"[API成功] 上报违章成功, ID: {violation_id}")
                    return violation_id
                else:
                    print(f"[API错误] 响应码: {result.get('code')}, 消息: {result.get('message')}")
            else:
                print(f"[API错误] HTTP状态码: {response.status_code}, 响应: {response.text}")

        except requests.exceptions.Timeout:
            print(f"[API超时] 上报违章请求超时")
        except requests.exceptions.ConnectionError:
            print(f"[API连接失败] 无法连接到后端服务器: {self.base_url}")
        except Exception as e:
            print(f"[API异常] 上报违章异常: {type(e).__name__}: {e}")

        return None

    def get_signal_status(self, intersection_id: int, direction: str, turn_type: str) -> Optional[str]:
        """
        获取指定方向和转弯类型的信号灯状态

        Args:
            intersection_id: 路口ID
            direction: 方向 (EAST/SOUTH/WEST/NORTH)
            turn_type: 转弯类型 (STRAIGHT/LEFT_TURN/RIGHT_TURN)

        Returns:
            信号灯状态 (RED/GREEN/YELLOW)，失败返回None
        """
        url = f"{self.base_url}/multi-direction-traffic/intersections/{intersection_id}/directions/{direction}/turns/{turn_type}/status"
        try:
            response = self.session.get(url, timeout=5)

            if response.status_code == 200:
                data = response.json()
                current_phase = data.get('currentPhase')
                print(f"[API] 信号灯状态: {direction} {turn_type} -> {current_phase}")
                return current_phase
            else:
                print(f"[API错误] 获取信号灯状态失败: {response.status_code}")

        except requests.exceptions.Timeout:
            print(f"[API超时] 获取信号灯状态超时")
        except requests.exceptions.ConnectionError:
            print(f"[API连接失败] 无法连接到后端服务器")
        except Exception as e:
            print(f"[API异常] 获取信号灯状态异常: {type(e).__name__}: {e}")

        return None

    def validate_violation(self, intersection_id: int, direction: str,
                         turn_type: str, violation_type: str) -> bool:
        """
        验证违章是否成立（根据当前信号灯状态）

        Args:
            intersection_id: 路口ID
            direction: 方向 (EAST/SOUTH/WEST/NORTH)
            turn_type: 转弯类型 (STRAIGHT/LEFT_TURN/RIGHT_TURN)
            violation_type: 违章类型 (RED_LIGHT/WRONG_WAY/CROSS_SOLID_LINE/ILLEGAL_TURN)

        Returns:
            True表示构成违章，False表示不构成违章
        """
        url = f"{self.base_url}/multi-direction-traffic/intersections/{intersection_id}/validate-violation"
        data = {
            "direction": direction,
            "turnType": turn_type,
            "violationType": violation_type
        }

        try:
            response = self.session.post(url, json=data, timeout=5)

            if response.status_code == 200:
                result = response.json()
                is_violation = result.get('isViolation', False)
                message = result.get('message', '')
                print(f"[API] 违章验证: {is_violation} - {message}")
                return is_violation
            else:
                print(f"[API错误] 验证违章失败: {response.status_code}")

        except Exception as e:
            print(f"[API异常] 验证违章异常: {type(e).__name__}: {e}")

        # 出错时返回False，避免误报
        return False

    def get_intersection_status(self, intersection_id: int) -> Optional[Dict]:
        """
        获取路口所有方向的信号灯状态

        Args:
            intersection_id: 路口ID

        Returns:
            包含所有方向状态的字典，失败返回None
        """
        url = f"{self.base_url}/multi-direction-traffic/intersections/{intersection_id}/status"
        try:
            response = self.session.get(url, timeout=5)

            if response.status_code == 200:
                return response.json()
            else:
                print(f"[API错误] 获取路口状态失败: {response.status_code}")

        except Exception as e:
            print(f"[API异常] 获取路口状态异常: {type(e).__name__}: {e}")

        return None

    def upload_image(self, image_path: str) -> str:
        """
        上传违章截图到服务器

        注意：当前后端可能没有文件上传接口，此方法返回本地文件路径
        如果后端有MinIO或其他对象存储接口，需要修改此方法

        Args:
            image_path: 本地图片路径

        Returns:
            图片URL（当前返回本地file://路径）
        """
        # TODO: 如果后端提供文件上传接口，实现真实上传逻辑
        # 示例：
        # url = f"{self.base_url}/upload"
        # with open(image_path, 'rb') as f:
        #     files = {'file': f}
        #     response = self.session.post(url, files=files)
        #     return response.json().get('url')

        # 当前返回本地路径
        return f"file://{image_path}"

    def health_check(self) -> bool:
        """
        健康检查：测试后端是否可访问

        Returns:
            True表示后端正常，False表示后端不可用
        """
        url = f"{self.base_url}/health"
        try:
            response = self.session.get(url, timeout=3)
            if response.status_code == 200:
                print(f"[API] 后端服务健康检查通过")
                return True
        except Exception as e:
            print(f"[API] 后端服务不可用: {type(e).__name__}: {e}")

        return False


# 测试代码
if __name__ == "__main__":
    print("=" * 60)
    print("后端API客户端测试")
    print("=" * 60)

    # 创建客户端
    client = BackendAPIClient("http://localhost:8081/api")

    # 1. 健康检查
    print("\n1. 健康检查...")
    is_healthy = client.health_check()
    print(f"   结果: {'✓ 成功' if is_healthy else '✗ 失败'}")

    if not is_healthy:
        print("\n⚠️  后端服务不可用，请确保后端已启动在 http://localhost:8080")
        exit(1)

    # 2. 获取信号灯状态
    print("\n2. 获取信号灯状态...")
    status = client.get_signal_status(1, 'SOUTH', 'STRAIGHT')
    print(f"   南向直行信号灯: {status}")

    # 3. 验证违章
    print("\n3. 验证违章是否成立...")
    is_violation = client.validate_violation(1, 'SOUTH', 'STRAIGHT', 'RED_LIGHT')
    print(f"   闯红灯违章: {is_violation}")

    # 4. 上报违章
    print("\n4. 上报违章...")
    test_violation = {
        'intersectionId': 1,
        'direction': 'SOUTH',
        'turnType': 'STRAIGHT',
        'plateNumber': '京A12345',
        'violationType': 'RED_LIGHT',
        'imageUrl': 'file:///test/violation_001.jpg',
        'aiConfidence': 0.95,
        'occurredAt': datetime.now().isoformat()
    }
    violation_id = client.report_violation(test_violation)
    print(f"   违章ID: {violation_id}")

    print("\n" + "=" * 60)
    print("测试完成!")
    print("=" * 60)
