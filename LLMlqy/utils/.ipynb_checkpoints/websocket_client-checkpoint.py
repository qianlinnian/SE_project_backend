"""
websocket_client.py - WebSocket客户端管理类
用于实时发送交通信号控制数据到远程服务器
"""
import json
import threading
import time
from typing import Optional, Dict, Any
import requests

try:
    import websocket
    WEBSOCKET_AVAILABLE = True
except ImportError:
    WEBSOCKET_AVAILABLE = False
    print("[警告] websocket-client未安装，将使用HTTP备用方案")
    print("[提示] 安装: pip install websocket-client")


class WebSocketClient:
    """
    WebSocket客户端，支持自动重连和心跳
    """
    
    def __init__(self, ws_url: str, 
                 reconnect: bool = True, 
                 heartbeat_interval: int = 30,
                 http_fallback_url: Optional[str] = None):
        """
        初始化WebSocket客户端
        
        Args:
            ws_url: WebSocket服务器URL
            reconnect: 是否自动重连
            heartbeat_interval: 心跳间隔（秒）
            http_fallback_url: HTTP备用URL
        """
        if not WEBSOCKET_AVAILABLE:
            raise ImportError("需要安装 websocket-client: pip install websocket-client")
            
        self.ws_url = ws_url
        self.reconnect = reconnect
        self.heartbeat_interval = heartbeat_interval
        self.http_fallback_url = http_fallback_url
        
        self.ws = None
        self.connected = False
        self.running = False
        
        # 统计信息
        self.messages_sent = 0
        self.messages_failed = 0
        self.last_send_time = None
        
        # 线程
        self.heartbeat_thread = None
        self.reconnect_thread = None
        
        print(f"[WebSocket] 初始化客户端: {ws_url}")
    
    def connect(self):
        """连接到WebSocket服务器"""
        try:
            print(f"[WebSocket] 正在连接: {self.ws_url}")
            
            self.ws = websocket.WebSocketApp(
                self.ws_url,
                on_open=self._on_open,
                on_message=self._on_message,
                on_error=self._on_error,
                on_close=self._on_close
            )
            
            # 在单独的线程中运行
            ws_thread = threading.Thread(target=self.ws.run_forever, daemon=True)
            ws_thread.start()
            
            # 等待连接建立
            timeout = 5
            start_time = time.time()
            while not self.connected and (time.time() - start_time) < timeout:
                time.sleep(0.1)
            
            if self.connected:
                print(f"[WebSocket] 连接成功")
                self.running = True
                
                # 启动心跳线程
                if self.heartbeat_interval > 0:
                    self.heartbeat_thread = threading.Thread(target=self._heartbeat_loop, daemon=True)
                    self.heartbeat_thread.start()
                
                return True
            else:
                print(f"[WebSocket] 连接超时")
                return False
                
        except Exception as e:
            print(f"[WebSocket] 连接失败: {e}")
            self.connected = False
            return False
    
    def _on_open(self, ws):
        """连接打开回调"""
        self.connected = True
        print(f"[WebSocket] 连接已建立")
    
    def _on_message(self, ws, message):
        """接收消息回调"""
        try:
            data = json.loads(message)
            msg_type = data.get('type', 'unknown')
            print(f"[WebSocket] 收到{msg_type}消息")
        except:
            print(f"[WebSocket] 收到消息: {message[:100]}...")
    
    def _on_error(self, ws, error):
        """错误回调"""
        print(f"[WebSocket] 错误: {error}")
    
    def _on_close(self, ws, close_status_code, close_msg):
        """连接关闭回调"""
        self.connected = False
        print(f"[WebSocket] 连接关闭 (code: {close_status_code})")
        
        # 自动重连
        if self.reconnect and self.running:
            print(f"[WebSocket] 3秒后尝试重连...")
            time.sleep(3)
            self.connect()
    
    def _heartbeat_loop(self):
        """心跳循环"""
        while self.running:
            try:
                if self.connected:
                    heartbeat_msg = {
                        "type": "heartbeat",
                        "timestamp": time.time()
                    }
                    self.send_json(heartbeat_msg, is_heartbeat=True)
                time.sleep(self.heartbeat_interval)
            except Exception as e:
                print(f"[WebSocket] 心跳失败: {e}")
    
    def send_json(self, data: Dict[str, Any], is_heartbeat: bool = False) -> bool:
        """
        发送JSON数据
        
        Args:
            data: 要发送的数据字典
            is_heartbeat: 是否是心跳消息
            
        Returns:
            是否发送成功
        """
        try:
            if self.connected and self.ws:
                message = json.dumps(data)
                self.ws.send(message)
                
                if not is_heartbeat:
                    self.messages_sent += 1
                    self.last_send_time = time.time()
                    
                return True
            else:
                # 使用HTTP备用接口
                if self.http_fallback_url and not is_heartbeat:
                    return self._send_http(data)
                else:
                    if not is_heartbeat:
                        self.messages_failed += 1
                    return False
                    
        except Exception as e:
            if not is_heartbeat:
                print(f"[WebSocket] 发送失败: {e}")
                self.messages_failed += 1
                
                # 尝试HTTP备用
                if self.http_fallback_url:
                    return self._send_http(data)
            return False
    
    def _send_http(self, data: Dict[str, Any]) -> bool:
        """使用HTTP发送数据（备用方案）"""
        try:
            response = requests.post(
                self.http_fallback_url,
                json=data,
                timeout=2
            )
            if response.status_code == 200:
                self.messages_sent += 1
                return True
            else:
                self.messages_failed += 1
                return False
        except Exception as e:
            self.messages_failed += 1
            return False
    
    def send_traffic_data(self, step: int, current_time: float, 
                         action_list: list, current_states: list,
                         roadnet: str, trafficflow: str) -> bool:
        """
        发送交通信号控制数据
        
        Args:
            step: 当前步数
            current_time: 仿真时间
            action_list: 动作列表
            current_states: 状态列表
            roadnet: 路网名称
            trafficflow: 交通流名称
            
        Returns:
            是否发送成功
        """
        # 构建数据包
        data = {
            "type": "traffic_signal_control",
            "timestamp": float(current_time),
            "step": step,
            "roadnet": roadnet,
            "trafficflow": trafficflow,
            "total_intersections": len(action_list),
            "intersections": []
        }
        
        # 添加每个路口的数据
        for i in range(len(action_list)):
            intersection_data = {
                "id": i,
                "signal_phase": action_list[i],
                "phase_code": action_list[i],
            }
            
            # 添加状态信息（如果可用）
            if i < len(current_states) and current_states[i]:
                try:
                    queue_length = sum(
                        current_states[i].get(lane, {}).get('queue_len', 0)
                        for lane in current_states[i]
                    )
                    intersection_data["queue_length"] = int(queue_length)
                except:
                    pass
            
            data["intersections"].append(intersection_data)
        
        return self.send_json(data)
    
    def get_stats(self) -> Dict[str, Any]:
        """获取统计信息"""
        return {
            "connected": self.connected,
            "messages_sent": self.messages_sent,
            "messages_failed": self.messages_failed,
            "last_send_time": self.last_send_time,
            "success_rate": self.messages_sent / max(1, self.messages_sent + self.messages_failed)
        }
    
    def close(self):
        """关闭连接"""
        print(f"[WebSocket] 正在关闭连接...")
        self.running = False
        
        if self.ws:
            self.ws.close()
        
        # 打印统计信息
        stats = self.get_stats()
        print(f"[WebSocket] 统计信息:")
        print(f"  - 已发送: {stats['messages_sent']}")
        print(f"  - 失败: {stats['messages_failed']}")
        print(f"  - 成功率: {stats['success_rate']:.2%}")


# 简化的HTTP客户端（作为WebSocket的替代）
class SimpleHTTPClient:
    """
    简化版HTTP客户端，当WebSocket不可用时使用
    """
    
    def __init__(self, http_url: str):
        self.http_url = http_url
        self.messages_sent = 0
        self.messages_failed = 0
        self.last_send_time = None
        print(f"[HTTP] 使用HTTP接口: {http_url}")
    
    def connect(self):
        return True
    
    def send_json(self, data: Dict[str, Any], is_heartbeat: bool = False) -> bool:
        if is_heartbeat:
            return True
            
        try:
            response = requests.post(
                self.http_url,
                json=data,
                timeout=2,
                headers={"Content-Type": "application/json"}
            )
            
            if response.status_code == 200:
                self.messages_sent += 1
                self.last_send_time = time.time()
                return True
            else:
                self.messages_failed += 1
                if self.messages_failed % 10 == 1:
                    print(f"[HTTP] 服务器返回: {response.status_code}")
                return False
                
        except Exception as e:
            self.messages_failed += 1
            if self.messages_failed == 1 or self.messages_failed % 20 == 0:
                print(f"[HTTP] 发送失败: {e}")
            return False
    
    def send_traffic_data(self, step: int, current_time: float, 
                         action_list: list, current_states: list,
                         roadnet: str, trafficflow: str) -> bool:
        data = {
            "type": "traffic_signal_control",
            "timestamp": float(current_time),
            "step": step,
            "roadnet": roadnet,
            "trafficflow": trafficflow,
            "total_intersections": len(action_list),
            "intersections": [
                {
                    "id": i,
                    "signal_phase": action_list[i],
                    "phase_code": action_list[i],
                } for i in range(len(action_list))
            ]
        }
        return self.send_json(data)
    
    def get_stats(self):
        return {
            "connected": True,
            "messages_sent": self.messages_sent,
            "messages_failed": self.messages_failed,
            "last_send_time": self.last_send_time,
            "success_rate": self.messages_sent / max(1, self.messages_sent + self.messages_failed)
        }
    
    def close(self):
        stats = self.get_stats()
        print(f"[HTTP] 统计信息: 成功={stats['messages_sent']}, 失败={stats['messages_failed']}, 成功率={stats['success_rate']:.2%}")


def create_client(ws_url: Optional[str] = None,
                 http_url: Optional[str] = None,
                 reconnect: bool = True,
                 heartbeat_interval: int = 30) -> Optional[Any]:
    """
    工厂函数：创建合适的客户端
    
    Args:
        ws_url: WebSocket URL
        http_url: HTTP URL
        reconnect: 是否自动重连
        heartbeat_interval: 心跳间隔
        
    Returns:
        客户端实例或None
    """
    # 优先使用WebSocket
    if ws_url and WEBSOCKET_AVAILABLE:
        try:
            client = WebSocketClient(ws_url, reconnect, heartbeat_interval, http_url)
            if client.connect():
                return client
            else:
                print("[警告] WebSocket连接失败，尝试使用HTTP")
        except Exception as e:
            print(f"[警告] WebSocket初始化失败: {e}")
    
    # 备用HTTP
    if http_url:
        return SimpleHTTPClient(http_url)
    
    print("[警告] 未配置有效的通信方式")
    return None