# TrafficMind Backend Service Dockerfile
# Multi-stage build for Java Spring Boot Application

# ============ 构建阶段 ============
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# 安装 Maven（如果 mvnw 不存在，使用系统 Maven）
RUN apk add --no-cache maven

# 复制 pom.xml（优先利用缓存）
COPY pom.xml .

# 下载依赖（利用 Docker 缓存层）
RUN mvn dependency:go-offline -B || true

# 复制源代码
COPY src ./src

# 打包应用（跳过测试以加快构建速度）
RUN mvn clean package -DskipTests -B

# ============ 运行阶段 ============
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 安装必要的工具
RUN apk add --no-cache curl

# 从构建阶段复制 JAR 文件
COPY --from=builder /app/target/*.jar app.jar

# 创建必要的目录
RUN mkdir -p /app/uploads/violation \
    /app/uploads/general \
    /app/logs

# 创建非 root 用户
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser && \
    chown -R appuser:appuser /app

# 切换到非 root 用户
USER appuser

# 设置环境变量
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENV SPRING_PROFILES_ACTIVE=prod

# 暴露端口
EXPOSE 8081

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
