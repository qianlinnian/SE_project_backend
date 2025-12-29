"""
后端API客户端 - TrafficMind 交通智脑
用于违规检测模块与后端API的通信
"""

import requests
from typing import Dict, Optional
from datetime import datetime


import os

# 从环境变量读取后端地址，默认使用服务器地址
DEFAULT_BASE_URL = os.getenv("BACKEND_BASE_URL", "http://47.107.50.136:8081/api")


class BackendAPIClient:
    """后端API客户端"""

    def __init__(self, base_url: str = None, username: str = "police001", password: str = "password123"):
        """
        初始化API客户端

        Args:
            base_url: 后端API基础URL，默认为环境变量 BACKEND_BASE_URL
            username: 登录用户名，默认为 police001
            password: 登录密码，默认为 password123
        """
        self.base_url = base_url or DEFAULT_BASE_URL
        self.username = username
        self.password = password
        self.jwt_token = None

        self.session = requests.Session()
        self.session.headers.update({
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        })

        # 初始化时自动登录获取 JWT token
        self._login()

    def _login(self):
        """
        登录获取 JWT token
        """
        try:
            url = f"{self.base_url}/auth/login"
            login_data = {
                "username": self.username,
                "password": self.password
            }
            response = requests.post(url, json=login_data, timeout=5)

            if response.status_code == 200:
                result = response.json()
                # Java后端返回的字段名是 'accessToken'，不是 'token'
                self.jwt_token = result.get('data', {}).get('accessToken')
                if self.jwt_token:
                    # 更新 session header，添加 Authorization
                    self.session.headers.update({
                        'Authorization': f'Bearer {self.jwt_token}'
                    })
                    print(f"[API] ✅ JWT 认证成功，用户: {self.username}")
                else:
                    print(f"[API] ❌ 登录响应中未找到 token")
                    print(f"[API] 响应内容: {result}")
            else:
                print(f"[API] ❌ 登录失败 HTTP {response.status_code}")
                try:
                    print(f"[API] 错误详情: {response.json()}")
                except:
                    print(f"[API] 错误详情: {response.text}")

        except Exception as e:
            print(f"[API] 登录异常: {type(e).__name__}: {e}")

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
            response = self.session.post(url, json=violation_data, timeout=3)

            if response.status_code == 200:
                result = response.json()
                # Java后端的违规上报接口直接返回 {'id': xxx, 'message': '...'}
                # 不是标准的 {'code': 200, 'data': {...}} 格式
                if 'id' in result:
                    violation_id = result.get('id')
                    print(f"[API] ✅ 上报成功! 后端ID: {violation_id}")
                    return violation_id
                # 兼容准格式（如果后端改了格式）
                elif result.get('code') == 200:
                    violation_id = result.get('data', {}).get('id')
                    #print(f"[API] ✅ 上报成功! 后端ID: {violation_id}")
                    return violation_id
                else:
                    print(f"[API] ❌ 上报失败: {result.get('message')}")
            else:
                print(f"[API] ❌ HTTP {response.status_code}: {response.text}")

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

        Args:
            image_path: 本地图片路径

        Returns:
            图片URL（成功返回后端URL，失败返回本地路径）
        """
        import os

        if not os.path.exists(image_path):
            print(f"[API]  文件不存在: {image_path}")
            return f"file://{image_path}"

        try:
            url = f"{self.base_url}/files/upload"
            with open(image_path, 'rb') as f:
                files = {'file': (os.path.basename(image_path), f, 'image/jpeg')}
                data = {'type': 'violation'}
                # 只保留 Authorization header，让 requests 自动设置 Content-Type 为 multipart/form-data
                headers = {
                    'Authorization': f'Bearer {self.jwt_token}'
                }
                response = requests.post(url, files=files, data=data, headers=headers, timeout=5)

            if response.status_code == 200:
                result = response.json()
                if result.get('success'):
                    image_url = result.get('url')
                    print(f"[API]  图片上传成功: {image_url}")
                    return image_url
                else:
                    print(f"[API]  图片上传失败: {result.get('message')}")
            else:
                print(f"[API]  图片上传失败 HTTP {response.status_code}")
                try:
                    error_detail = response.json()
                    print(f"[API]  错误详情: {error_detail}")
                except:
                    print(f"[API]  错误详情: {response.text}")

        except Exception as e:
            print(f"[API]  图片上传异常: {type(e).__name__}: {e}")

        # 上传失败，返回本地路径作为备选
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