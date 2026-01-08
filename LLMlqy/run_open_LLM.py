"""
run_open_LLM.py - 使用开源LLM进行交通信号控制
支持实时发送数据到远程服务器
"""
import os
import time
import copy
import argparse
from utils.llm_inference import LLM_Inference
from utils.config import dic_traffic_env_conf


def merge(dic_tmp, dic_to_change):
    """合并两个字典"""
    dic_result = copy.deepcopy(dic_tmp)
    dic_result.update(dic_to_change)
    return dic_result


def parse_args():
    parser = argparse.ArgumentParser(description="LLM Traffic Signal Control")
    
    # 模型相关
    parser.add_argument("--llm_model", type=str, default="Qwen2.5-3B",
                        help="模型名称（仅用于日志）")
    parser.add_argument("--llm_path", type=str, default="../Qwen2.5-3B",
                        help="模型路径")
    parser.add_argument("--new_max_tokens", type=int, default=512,
                        help="生成的最大token数")
    
    # 仿真相关
    parser.add_argument("--dataset", type=str, default="hangzhou",
                        choices=["jinan", "hangzhou", "newyork_28x7"],
                        help="数据集名称")
    parser.add_argument("--traffic_file", type=str, default="anon_4_4_hangzhou_real.json",
                        help="交通流文件")
    parser.add_argument("--eightphase", action="store_true", default=False,
                        help="是否使用8相位")
    
    # 日志相关
    parser.add_argument("--memo", type=str, default='LLMLight')
    parser.add_argument("--proj_name", type=str, default="TSCS")
    
    # 远程服务器配置
    parser.add_argument("--remote_url", type=str, default=None,
                        help="远程服务器URL，如 http://47.107.50.136:8080/api/traffic")
    parser.add_argument("--enable_remote", action="store_true", default=False,
                        help="是否启用远程数据传输")
    
    return parser.parse_args()


def main(args):
    # 数据集配置
    DATASET_CONFIG = {
        'jinan': {
            'road_net': "3_4",
            'traffic_files': [
                "anon_3_4_jinan_real.json",
                "anon_3_4_jinan_real_2000.json",
                "anon_3_4_jinan_real_2500.json"
            ],
            'template': "Jinan"
        },
        'hangzhou': {
            'road_net': "4_4",
            'traffic_files': [
                "anon_4_4_hangzhou_real.json",
                "anon_4_4_hangzhou_real_5816.json"
            ],
            'template': "Hangzhou"
        },
        'newyork_28x7': {
            'road_net': "28_7",
            'traffic_files': [
                "anon_28_7_newyork_real_double.json",
                "anon_28_7_newyork_real_triple.json"
            ],
            'template': "NewYork"
        }
    }
    
    if args.dataset not in DATASET_CONFIG:
        raise ValueError(f"未知数据集: {args.dataset}")
    
    config = DATASET_CONFIG[args.dataset]
    road_net = config['road_net']
    template = config['template']
    
    # 验证交通流文件
    if args.traffic_file not in config['traffic_files']:
        print(f"警告: {args.traffic_file} 不在预设列表中，继续运行...")
    
    # 计算路口数量
    NUM_ROW = int(road_net.split('_')[0])
    NUM_COL = int(road_net.split('_')[1])
    num_intersections = NUM_ROW * NUM_COL
    
    print(f"=" * 50)
    print(f"数据集: {args.dataset}")
    print(f"路网: {road_net} ({NUM_ROW}x{NUM_COL})")
    print(f"路口数量: {num_intersections}")
    print(f"交通流文件: {args.traffic_file}")
    print(f"LLM模型: {args.llm_path}")
    if args.enable_remote:
        print(f"远程服务器: {args.remote_url}")
    print(f"=" * 50)
    
    # Agent配置
    dic_agent_conf = {
        "LLM_PATH": args.llm_path,
        "LLM_MODEL": args.llm_model,
        "NEW_MAX_TOKENS": args.new_max_tokens,
        "FIXED_TIME": [30, 30, 30, 30] if not args.eightphase else [30] * 8
    }
    
    # 环境配置
    dic_traffic_env_conf_extra = {
        "NUM_AGENTS": num_intersections,
        "NUM_INTERSECTIONS": num_intersections,
        "MODEL_NAME": f"{args.memo}-{args.llm_model}",
        "PROJECT_NAME": args.proj_name,
        "RUN_COUNTS": 3600,  # 仿真1小时
        "NUM_ROW": NUM_ROW,
        "NUM_COL": NUM_COL,
        "TRAFFIC_FILE": args.traffic_file,
        "ROADNET_FILE": f"roadnet_{road_net}.json",
    }
    
    # 8相位配置
    if args.eightphase:
        dic_traffic_env_conf_extra["PHASE"] = {
            1: [0, 1, 0, 1, 0, 0, 0, 0], 2: [0, 0, 0, 0, 0, 1, 0, 1],
            3: [1, 0, 1, 0, 0, 0, 0, 0], 4: [0, 0, 0, 0, 1, 0, 1, 0],
            5: [1, 1, 0, 0, 0, 0, 0, 0], 6: [0, 0, 1, 1, 0, 0, 0, 0],
            7: [0, 0, 0, 0, 0, 0, 1, 1], 8: [0, 0, 0, 0, 1, 1, 0, 0]
        }
        dic_traffic_env_conf_extra["PHASE_LIST"] = [
            'WT_ET', 'NT_ST', 'WL_EL', 'NL_SL',
            'WL_WT', 'EL_ET', 'SL_ST', 'NL_NT'
        ]
    
    # 路径配置
    timestamp = time.strftime('%m_%d_%H_%M_%S', time.localtime())
    dic_path = {
        "PATH_TO_MODEL": os.path.join("model", args.memo, f"{args.traffic_file}_{timestamp}"),
        "PATH_TO_WORK_DIRECTORY": os.path.join("records", args.memo, f"{args.traffic_file}_{timestamp}"),
        "PATH_TO_DATA": os.path.join("data", template, road_net)
    }
    
    # 合并配置
    final_env_conf = merge(dic_traffic_env_conf, dic_traffic_env_conf_extra)
    
    # 创建推理器并运行
    trainer = LLM_Inference(
        dic_agent_conf=dic_agent_conf,
        dic_traffic_env_conf=final_env_conf,
        dic_path=dic_path,
        roadnet=f'{template}-{road_net}',
        trafficflow=args.traffic_file.split(".")[0],
        remote_server_url=args.remote_url,
        enable_remote=args.enable_remote
    )
    
    trainer.run()


if __name__ == "__main__":
    args = parse_args()
    main(args)