package com.traffic.management.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traffic.management.entity.VideoAnalysisTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI模型集成服务
 * 负责与AI模型的通信和数据交换
 */
@Slf4j
@Service
public class AIIntegrationService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskStatusService taskStatusService;

    // AI服务配置
    @Value("${ai.service.base-url:http://localhost:5000}")
    private String aiServiceBaseUrl;

    @Value("${ai.service.analyze-endpoint:/analyze-video}")
    private String analyzeVideoEndpoint;

    @Value("${ai.service.timeout.connect:30}")
    private int connectTimeoutSeconds;

    @Value("${ai.service.timeout.read:300}")
    private int readTimeoutSeconds;

    @Value("${ai.service.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${ai.service.retry.delay:10}")
    private int retryDelaySeconds;

    @Value("${server.callback.base-url:http://localhost:8088}")
    private String callbackBaseUrl;

    @Value("${server.callback.ai-result-path:/api/violation-detection/ai-callback}")
    private String callbackResultPath;

    /**
     * 异步发送视频给AI模型分析
     */
    @Async
    public CompletableFuture<Void> analyzeVideoAsync(String taskId, String videoUrl,
            Long intersectionId, String direction) {
        return CompletableFuture.runAsync(() -> {
            analyzeVideoSync(taskId, videoUrl, intersectionId, direction);
        });
    }

    /**
     * 同步发送视频给AI模型分析
     */
    public void analyzeVideoSync(String taskId, String videoUrl,
            Long intersectionId, String direction) {
        log.info("开始向AI模型发送分析请求 - 任务ID: {}, 视频URL: {}", taskId, videoUrl);

        int attemptCount = 0;
        Exception lastException = null;

        while (attemptCount < maxRetryAttempts) {
            attemptCount++;

            try {
                // 更新任务状态为AI处理中
                if (attemptCount == 1) {
                    taskStatusService.updateTaskStatus(taskId,
                            VideoAnalysisTask.TaskStatus.AI_PROCESSING, null);
                }

                // 构建AI请求
                AIAnalysisRequest request = buildAIRequest(taskId, videoUrl, intersectionId, direction);

                // 发送请求
                AIAnalysisResponse response = sendAnalysisRequest(request);

                // 处理响应
                if (response != null && response.isSuccess()) {
                    log.info("AI分析请求发送成功 - 任务ID: {}, AI响应: {}", taskId, response.getMessage());

                    // 保存AI请求信息
                    taskStatusService.updateAIRequestInfo(taskId,
                            objectMapper.writeValueAsString(request),
                            buildCallbackUrl());
                    return;
                } else {
                    throw new RuntimeException("AI服务返回错误: " +
                            (response != null ? response.getMessage() : "无响应"));
                }

            } catch (Exception e) {
                lastException = e;
                log.error("AI分析请求失败 - 任务ID: {}, 尝试次数: {}/{}, 错误: {}",
                        taskId, attemptCount, maxRetryAttempts, e.getMessage());

                if (attemptCount < maxRetryAttempts) {
                    // 等待重试
                    try {
                        Thread.sleep(retryDelaySeconds * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    // 最终失败
                    taskStatusService.updateTaskStatus(taskId,
                            VideoAnalysisTask.TaskStatus.AI_FAILED,
                            "AI分析请求失败: " + e.getMessage());
                    taskStatusService.incrementRetryCount(taskId);
                }
            }
        }

        log.error("AI分析请求最终失败 - 任务ID: {}, 已尝试 {} 次", taskId, maxRetryAttempts);
    }

    /**
     * 构建AI分析请求
     */
    private AIAnalysisRequest buildAIRequest(String taskId, String videoUrl,
            Long intersectionId, String direction) {
        String callbackUrl = buildCallbackUrl();

        return AIAnalysisRequest.builder()
                .taskId(taskId)
                .videoUrl(videoUrl)
                .intersectionId(intersectionId)
                .direction(direction)
                .callbackUrl(callbackUrl)
                .requestTime(LocalDateTime.now())
                .build();
    }

    /**
     * 发送AI分析请求
     */
    private AIAnalysisResponse sendAnalysisRequest(AIAnalysisRequest request) throws Exception {
        String url = aiServiceBaseUrl + analyzeVideoEndpoint;

        // 构建HTTP头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("User-Agent", "TrafficManagement/1.0");
        headers.add("X-Request-ID", request.getTaskId());

        // 构建请求体
        HttpEntity<AIAnalysisRequest> entity = new HttpEntity<>(request, headers);

        try {
            // 发送请求
            ResponseEntity<AIAnalysisResponse> responseEntity = restTemplate.exchange(
                    url, HttpMethod.POST, entity, AIAnalysisResponse.class);

            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                return responseEntity.getBody();
            } else {
                throw new RuntimeException("HTTP错误: " + responseEntity.getStatusCode());
            }

        } catch (ResourceAccessException e) {
            if (e.getMessage().contains("timeout")) {
                throw new RuntimeException("AI服务连接超时", e);
            } else {
                throw new RuntimeException("AI服务连接错误", e);
            }
        }
    }

    /**
     * 构建回调URL
     */
    private String buildCallbackUrl() {
        return callbackBaseUrl + callbackResultPath;
    }

    /**
     * 检查AI服务健康状态
     */
    public boolean checkAIServiceHealth() {
        try {
            String healthUrl = aiServiceBaseUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.warn("AI服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取AI服务状态信息
     */
    public Map<String, Object> getAIServiceInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("baseUrl", aiServiceBaseUrl);
        info.put("analyzeEndpoint", analyzeVideoEndpoint);
        info.put("callbackUrl", buildCallbackUrl());
        info.put("maxRetryAttempts", maxRetryAttempts);
        info.put("retryDelaySeconds", retryDelaySeconds);
        info.put("connectTimeoutSeconds", connectTimeoutSeconds);
        info.put("readTimeoutSeconds", readTimeoutSeconds);
        info.put("healthStatus", checkAIServiceHealth());
        info.put("lastCheckedAt", LocalDateTime.now());
        return info;
    }

    /**
     * AI分析请求对象
     */
    public static class AIAnalysisRequest {
        private String taskId;
        private String videoUrl;
        private Long intersectionId;
        private String direction;
        private String callbackUrl;
        private LocalDateTime requestTime;

        // Builder pattern
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private final AIAnalysisRequest request = new AIAnalysisRequest();

            public Builder taskId(String taskId) {
                request.taskId = taskId;
                return this;
            }

            public Builder videoUrl(String videoUrl) {
                request.videoUrl = videoUrl;
                return this;
            }

            public Builder intersectionId(Long intersectionId) {
                request.intersectionId = intersectionId;
                return this;
            }

            public Builder direction(String direction) {
                request.direction = direction;
                return this;
            }

            public Builder callbackUrl(String callbackUrl) {
                request.callbackUrl = callbackUrl;
                return this;
            }

            public Builder requestTime(LocalDateTime requestTime) {
                request.requestTime = requestTime;
                return this;
            }

            public AIAnalysisRequest build() {
                return request;
            }
        }

        // Getters and setters
        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getVideoUrl() {
            return videoUrl;
        }

        public void setVideoUrl(String videoUrl) {
            this.videoUrl = videoUrl;
        }

        public Long getIntersectionId() {
            return intersectionId;
        }

        public void setIntersectionId(Long intersectionId) {
            this.intersectionId = intersectionId;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getCallbackUrl() {
            return callbackUrl;
        }

        public void setCallbackUrl(String callbackUrl) {
            this.callbackUrl = callbackUrl;
        }

        public LocalDateTime getRequestTime() {
            return requestTime;
        }

        public void setRequestTime(LocalDateTime requestTime) {
            this.requestTime = requestTime;
        }
    }

    /**
     * AI分析响应对象
     */
    public static class AIAnalysisResponse {
        private boolean success;
        private String message;
        private String taskId;
        private String estimatedProcessingTime;

        // Constructors
        public AIAnalysisResponse() {
        }

        public AIAnalysisResponse(boolean success, String message, String taskId) {
            this.success = success;
            this.message = message;
            this.taskId = taskId;
        }

        // Getters and setters
        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getEstimatedProcessingTime() {
            return estimatedProcessingTime;
        }

        public void setEstimatedProcessingTime(String estimatedProcessingTime) {
            this.estimatedProcessingTime = estimatedProcessingTime;
        }
    }
}