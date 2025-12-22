package com.traffic.management.service;

import com.traffic.management.entity.VideoAnalysisTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 通知服务
 * 负责发送实时通知给前端用户
 */
@Slf4j
@Service
public class NotificationService {

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 发送任务创建通知
     */
    public void sendTaskCreatedNotification(VideoAnalysisTask task) {
        Map<String, Object> notification = createBaseNotification(task, "TASK_CREATED");
        notification.put("message", "新任务已创建，视频上传完成");

        sendNotification(task.getTaskId(), notification);
        log.info("发送任务创建通知 - 任务ID: {}", task.getTaskId());
    }

    /**
     * 发送任务状态变化通知
     */
    public void sendTaskStatusChangeNotification(VideoAnalysisTask task, VideoAnalysisTask.TaskStatus oldStatus) {
        Map<String, Object> notification = createBaseNotification(task, "STATUS_CHANGED");
        notification.put("oldStatus", oldStatus.getDescription());
        notification.put("message", getStatusChangeMessage(oldStatus, task.getStatus()));

        sendNotification(task.getTaskId(), notification);
        log.info("发送状态变化通知 - 任务ID: {}, {} -> {}",
                task.getTaskId(), oldStatus, task.getStatus());
    }

    /**
     * 发送任务进度通知
     */
    public void sendTaskProgressNotification(VideoAnalysisTask task) {
        Map<String, Object> notification = createBaseNotification(task, "PROGRESS_UPDATE");
        notification.put("message", "任务进度更新: " + task.getProgress() + "%");

        sendNotification(task.getTaskId(), notification);
        log.debug("发送进度通知 - 任务ID: {}, 进度: {}%", task.getTaskId(), task.getProgress());
    }

    /**
     * 发送违章检测通知
     */
    public void sendViolationDetectedNotification(String taskId, Map<String, Object> violationInfo) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "VIOLATION_DETECTED");
        notification.put("taskId", taskId);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("violationInfo", violationInfo);
        notification.put("message", "检测到违章: " + violationInfo.get("violationType"));

        sendNotification(taskId, notification);
        log.info("发送违章检测通知 - 任务ID: {}, 违章类型: {}",
                taskId, violationInfo.get("violationType"));
    }

    /**
     * 发送任务完成通知
     */
    public void sendTaskCompletedNotification(VideoAnalysisTask task) {
        Map<String, Object> notification = createBaseNotification(task, "TASK_COMPLETED");
        notification.put("message", String.format("任务完成！检测到 %d 个违章，耗时 %d 秒",
                task.getTotalViolations(), task.getExecutionDurationSeconds()));

        // 添加完成统计
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalViolations", task.getTotalViolations());
        summary.put("processedViolations", task.getProcessedViolations());
        summary.put("executionTime", task.getExecutionDurationSeconds());
        summary.put("aiProcessingTime", task.getAiProcessingDurationSeconds());
        notification.put("summary", summary);

        sendNotification(task.getTaskId(), notification);
        log.info("发送任务完成通知 - 任务ID: {}, 违章数: {}",
                task.getTaskId(), task.getTotalViolations());
    }

    /**
     * 发送错误通知
     */
    public void sendErrorNotification(String taskId, String errorType, String errorMessage) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "ERROR_OCCURRED");
        notification.put("taskId", taskId);
        notification.put("timestamp", LocalDateTime.now());
        notification.put("errorType", errorType);
        notification.put("errorMessage", errorMessage);
        notification.put("message", "任务出现错误: " + errorType);

        sendNotification(taskId, notification);
        log.warn("发送错误通知 - 任务ID: {}, 错误类型: {}, 错误信息: {}",
                taskId, errorType, errorMessage);
    }

    /**
     * 发送系统通知
     */
    public void sendSystemNotification(String message, String type) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "SYSTEM_NOTIFICATION");
        notification.put("message", message);
        notification.put("notificationType", type);
        notification.put("timestamp", LocalDateTime.now());

        // 发送给所有用户
        sendBroadcastNotification(notification);
        log.info("发送系统通知 - 类型: {}, 消息: {}", type, message);
    }

    /**
     * 创建基础通知对象
     */
    private Map<String, Object> createBaseNotification(VideoAnalysisTask task, String type) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", type);
        notification.put("taskId", task.getTaskId());
        notification.put("intersectionId", task.getIntersectionId());
        notification.put("direction", task.getDirection().name());
        notification.put("directionName", task.getDirection().getChineseName());
        notification.put("status", task.getStatus().name());
        notification.put("statusDescription", task.getStatus().getDescription());
        notification.put("progress", task.getProgress());
        notification.put("timestamp", LocalDateTime.now());
        return notification;
    }

    /**
     * 生成状态变化消息
     */
    private String getStatusChangeMessage(VideoAnalysisTask.TaskStatus oldStatus,
            VideoAnalysisTask.TaskStatus newStatus) {
        return switch (newStatus) {
            case AI_PROCESSING -> "AI模型正在分析视频...";
            case AI_COMPLETED -> "AI分析完成，正在验证违章结果";
            case VALIDATION_COMPLETED -> "任务完成！违章检测和验证已完成";
            case AI_FAILED -> "AI分析失败，请检查视频文件或联系管理员";
            case FAILED -> "任务失败，请重试或联系管理员";
            default -> "任务状态已更新: " + newStatus.getDescription();
        };
    }

    /**
     * 发送通知给特定任务的订阅者
     */
    private void sendNotification(String taskId, Map<String, Object> notification) {
        if (messagingTemplate != null) {
            try {
                // 发送给特定任务订阅者
                messagingTemplate.convertAndSend("/topic/task/" + taskId, notification);

                // 发送给所有任务订阅者（用于总览页面）
                messagingTemplate.convertAndSend("/topic/tasks", notification);

            } catch (Exception e) {
                log.error("发送WebSocket通知失败 - 任务ID: {}", taskId, e);
            }
        } else {
            log.debug("WebSocket未配置，跳过通知发送 - 任务ID: {}", taskId);
        }
    }

    /**
     * 发送广播通知
     */
    private void sendBroadcastNotification(Map<String, Object> notification) {
        if (messagingTemplate != null) {
            try {
                messagingTemplate.convertAndSend("/topic/system", notification);
            } catch (Exception e) {
                log.error("发送系统广播通知失败", e);
            }
        }
    }

    /**
     * 检查WebSocket是否可用
     */
    public boolean isWebSocketAvailable() {
        return messagingTemplate != null;
    }

    /**
     * 获取通知服务状态
     */
    public Map<String, Object> getNotificationServiceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("webSocketEnabled", isWebSocketAvailable());
        status.put("timestamp", LocalDateTime.now());

        if (isWebSocketAvailable()) {
            status.put("endpoints", Map.of(
                    "taskNotifications", "/topic/task/{taskId}",
                    "allTasks", "/topic/tasks",
                    "systemNotifications", "/topic/system"));
        }

        return status;
    }
}