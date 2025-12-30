# TrafficMind AI Detection Service Dockerfile
# Python 3.10 + OpenCV + YOLOv8

FROM python:3.10-slim

# 设置工作目录
WORKDIR /app

# 配置国内镜像源（加速apt-get）
RUN sed -i 's/deb.debian.org/mirrors.ustc.edu.cn/g' /etc/apt/sources.list.d/debian.sources || \
    sed -i 's/deb.debian.org/mirrors.aliyun.com/g' /etc/apt/sources.list || true

# 安装系统依赖（OpenCV 需要）
# 注意：libgl1-mesa-glx 在新版本中已被 libgl1 替代
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    libgomp1 \
    libgstreamer1.0-0 \
    && rm -rf /var/lib/apt/lists/*

# 复制依赖文件（从 ai_detection 目录）
COPY ai_detection/requirements.txt .

# 配置 pip 国内镜像源（加速下载）
RUN pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/ && \
    pip config set install.trusted-host mirrors.aliyun.com

# 安装 Python 依赖
RUN pip install --no-cache-dir -r requirements.txt

# 复制 AI 检测相关代码（只复制需要的文件）
COPY ai_detection/ .

# 创建必要的目录
RUN mkdir -p /app/uploads \
    /app/output/screenshots \
    /app/violations \
    /app/logs

# 设置环境变量
ENV PYTHONUNBUFFERED=1
ENV BACKEND_API_URL=http://backend:8081/api

# 暴露端口
EXPOSE 5000

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD python -c "import requests; requests.get('http://localhost:5000/health', timeout=5)"

# 启动命令
CMD ["python", "api/ai_realtime_service.py"]
