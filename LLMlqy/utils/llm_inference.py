"""
llm_inference.py - LLM推理核心类
负责加载模型、运行仿真、发送数据
"""
import os
import time
import copy
import json
import shutil
import re
import numpy as np
import requests
import torch
from copy import deepcopy
from tqdm import tqdm
from transformers import AutoTokenizer, AutoModelForCausalLM

from utils.cityflow_env import CityFlowEnv
from utils.my_utils import (
    dump_json, load_json, get_state_detail, state2text, getPrompt,
    action2code, code2action, eight_phase_list, four_phase_list
)


class LLM_Inference:
    """
    LLM交通信号控制推理类
    """
    
    def __init__(self, dic_agent_conf, dic_traffic_env_conf, dic_path, 
                 roadnet, trafficflow, remote_server_url=None, enable_remote=False):
        """
        初始化
        
        Args:
            dic_agent_conf: Agent配置（包含LLM路径等）
            dic_traffic_env_conf: 交通环境配置
            dic_path: 路径配置
            roadnet: 路网名称
            trafficflow: 交通流名称
            remote_server_url: 远程服务器URL
            enable_remote: 是否启用远程发送
        """
        self.dic_agent_conf = dic_agent_conf
        self.dic_traffic_env_conf = dic_traffic_env_conf
        self.dic_path = dic_path
        self.roadnet = roadnet
        self.trafficflow = trafficflow
        
        # 远程服务器配置
        self.remote_server_url = remote_server_url
        self.enable_remote = enable_remote
        
        # LLM相关
        self.llm_model = None
        self.tokenizer = None
        self.generation_kwargs = {}
        
        # 仿真环境
        self.env = None
        
        # 初始化
        self._setup_directories()
        self._setup_environment()
        self._setup_llm()
    
    def _setup_directories(self):
        """创建必要的目录"""
        os.makedirs(self.dic_path["PATH_TO_WORK_DIRECTORY"], exist_ok=True)
        os.makedirs(self.dic_path["PATH_TO_MODEL"], exist_ok=True)
        os.makedirs("./logs", exist_ok=True)
        
        # 复制配置文件
        work_dir = self.dic_path["PATH_TO_WORK_DIRECTORY"]
        json.dump(self.dic_agent_conf, open(os.path.join(work_dir, "agent.conf"), "w"), indent=2)
        json.dump(self.dic_traffic_env_conf, open(os.path.join(work_dir, "traffic_env.conf"), "w"), indent=2)
        
        # 复制CityFlow文件
        data_dir = self.dic_path["PATH_TO_DATA"]
        shutil.copy(
            os.path.join(data_dir, self.dic_traffic_env_conf["TRAFFIC_FILE"]),
            os.path.join(work_dir, self.dic_traffic_env_conf["TRAFFIC_FILE"])
        )
        shutil.copy(
            os.path.join(data_dir, self.dic_traffic_env_conf["ROADNET_FILE"]),
            os.path.join(work_dir, self.dic_traffic_env_conf["ROADNET_FILE"])
        )
    
    def _setup_environment(self):
        """初始化CityFlow仿真环境"""
        self.env = CityFlowEnv(
            path_to_log=self.dic_path["PATH_TO_WORK_DIRECTORY"],
            path_to_work_directory=self.dic_path["PATH_TO_WORK_DIRECTORY"],
            dic_traffic_env_conf=self.dic_traffic_env_conf,
            dic_path=self.dic_path
        )
        self.env.reset()
        print("CityFlow环境初始化完成")
    
    def _setup_llm(self):
        """加载LLM模型"""
        llm_path = self.dic_agent_conf["LLM_PATH"]
        print(f"正在加载LLM模型: {llm_path}")
        
        # 加载模型
        self.llm_model = AutoModelForCausalLM.from_pretrained(
            llm_path,
            torch_dtype=torch.bfloat16,
            device_map="auto",
            trust_remote_code=True
        )
        
        # 加载Tokenizer
        self.tokenizer = AutoTokenizer.from_pretrained(
            llm_path,
            padding_side="left",
            trust_remote_code=True
        )
        if self.tokenizer.pad_token_id is None:
            self.tokenizer.pad_token_id = self.tokenizer.eos_token_id
        
        # 生成参数
        self.generation_kwargs = {
            "max_new_tokens": self.dic_agent_conf["NEW_MAX_TOKENS"],
            "do_sample": True,
            "temperature": 0.1,
            "top_p": 0.9,
            "pad_token_id": self.tokenizer.pad_token_id,
            "eos_token_id": self.tokenizer.eos_token_id
        }
        
        print("LLM模型加载完成")
    
    def run(self):
        """运行仿真主循环"""
        print("\n" + "=" * 50)
        print("开始交通信号控制仿真")
        print("=" * 50)
        
        total_run_cnt = self.dic_traffic_env_conf["RUN_COUNTS"]
        min_action_time = self.dic_traffic_env_conf.get("MIN_ACTION_TIME", 30)
        total_steps = int(total_run_cnt / min_action_time)
        
        # 初始化
        state = self.env.reset()
        done = False
        current_time = self.env.get_current_time()
        
        # 统计数据
        all_queue_lengths = []
        all_travel_times = []
        
        self.llm_model.eval()
        
        # 主循环
        for step in tqdm(range(total_steps), desc="仿真进度"):
            if done or current_time >= total_run_cnt:
                break
            
            # 1. 获取所有路口的状态
            current_states = []
            for i in range(len(state)):
                intersection = self.env.intersection_dict[self.env.list_intersection[i].inter_name]
                roads = deepcopy(intersection["roads"])
                statistic_state, _, _ = get_state_detail(roads, self.env)
                current_states.append(statistic_state)
            
            # 2. 生成Prompt并调用LLM
            action_list = self._get_llm_actions(current_states)
            
            # 3. 发送数据到远程服务器
            if self.enable_remote and self.remote_server_url:
                self._send_to_remote(step, current_time, action_list, current_states)
            
            # 4. 执行动作
            next_state, reward, done, info = self.env.step(action_list)
            
            # 5. 更新状态
            current_time = self.env.get_current_time()
            state = next_state
            
            # 6. 记录统计数据
            queue_length = sum(
                sum(inter.dic_feature['lane_num_waiting_vehicle_in'])
                for inter in self.env.list_intersection
            )
            all_queue_lengths.append(queue_length)
            
            # 每10步打印一次状态
            if step % 10 == 0:
                print(f"\n[Step {step}] 时间={current_time}s, 排队车辆={queue_length}")
        
        # 仿真结束，打印统计
        print("\n" + "=" * 50)
        print("仿真结束")
        print(f"平均排队车辆: {np.mean(all_queue_lengths):.2f}")
        print("=" * 50)
    
    def _get_llm_actions(self, current_states):
        """
        使用LLM为每个路口生成动作
        
        Args:
            current_states: 所有路口的状态列表
            
        Returns:
            action_list: 每个路口的动作编码列表
        """
        action_list = []
        
        # 为每个路口生成决策
        for i, state in enumerate(current_states):
            # 生成Prompt
            prompt_messages = getPrompt(state2text(state))
            prompt = prompt_messages[0]['content'] + "\n\n" + prompt_messages[1]['content']
            
            # Tokenize
            inputs = self.tokenizer(prompt, return_tensors="pt", truncation=True, max_length=2048)
            inputs = {k: v.to(self.llm_model.device) for k, v in inputs.items()}
            
            # 生成
            with torch.no_grad():
                outputs = self.llm_model.generate(**inputs, **self.generation_kwargs)
            
            # 解码
            response = self.tokenizer.decode(outputs[0], skip_special_tokens=True)
            response = response[len(prompt):]  # 去掉prompt部分
            
            # 解析动作
            action = self._parse_action(response)
            action_list.append(action)
        
        return action_list
    
    def _parse_action(self, response):
        """
        从LLM响应中解析动作
        
        Args:
            response: LLM的响应文本
            
        Returns:
            action: 动作编码 (0-3)
        """
        # 尝试匹配 <signal>XXXX</signal> 格式
        pattern = r'<signal>(.*?)</signal>'
        matches = re.findall(pattern, response)
        
        if matches:
            signal_text = matches[-1].strip().upper()
            if signal_text in four_phase_list:
                return action2code(signal_text)
        
        # 尝试直接匹配相位名称
        for phase_name in four_phase_list:
            if phase_name in response.upper():
                return action2code(phase_name)
        
        # 默认返回0 (ETWT)
        return 0
    
    def _send_to_remote(self, step, current_time, action_list, current_states):
        """
        发送实时数据到远程服务器
        
        Args:
            step: 当前步数
            current_time: 仿真时间
            action_list: 动作列表
            current_states: 状态列表
        """
        # 构建数据包
        data = {
            "timestamp": float(current_time),
            "step": step,
            "roadnet": self.roadnet,
            "trafficflow": self.trafficflow,
            "total_intersections": len(action_list),
            "intersections": []
        }
        
        for i in range(len(action_list)):
            # 计算该路口的统计数据
            queue_length = sum(
                current_states[i][lane]['queue_len']
                for lane in current_states[i]
            )
            vehicle_count = queue_length + sum(
                sum(current_states[i][lane]['cells'])
                for lane in current_states[i]
            )
            
            intersection_data = {
                "id": i,
                "signal_phase": eight_phase_list[action_list[i]],
                "phase_code": action_list[i],
                "queue_length": int(queue_length),
                "vehicle_count": int(vehicle_count),
                "lanes": current_states[i]
            }
            data["intersections"].append(intersection_data)
        
        # 发送HTTP请求
        try:
            response = requests.post(
                self.remote_server_url,
                json=data,
                timeout=2,
                headers={"Content-Type": "application/json"}
            )
            if response.status_code != 200:
                print(f"[警告] 服务器返回: {response.status_code}")
        except requests.exceptions.ConnectionError:
            print(f"[警告] 无法连接服务器: {self.remote_server_url}")
        except requests.exceptions.Timeout:
            print("[警告] 请求超时")
        except Exception as e:
            print(f"[警告] 发送失败: {e}")