package com.traffic.management.service;

import com.traffic.management.entity.VideoAnalysisTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务调度服务
 * 负责处理超时任务、重试失败任务、清理历史数据等
 */
@Slf4j
@Service
public class TaskScheduleService {

    @Autowired
    private TaskStatusService taskStatusService;

    @Autowired
    private AIIntegrationService aiIntegrationService;

    @Autowired
    private NotificationService notificationService;

    // 配置参数
    @Value("${task.schedule.timeout.ai-processing:30}")
    private int aiProcessingTimeoutMinutes;

    @Value("${task.schedule.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${task.schedule.cleanup.history-days:7}")
    private int historyRetentionDays;

    @Value("${task.schedule.enabled:true}")
    private boolean scheduleEnabled;

    /**
     * 检查并处理超时的AI任务
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void handleTimeoutTasks() {
        if (!scheduleEnabled) {
            return;
        }

        try {
            log.debug("开始检查超时任务...");

            List<VideoAnalysisTask> timeoutTasks = taskStatusService.getTimeoutAiTasks(aiProcessingTimeoutMinutes);

            if (!timeoutTasks.isEmpty()) {
                log.warn("发现 {} 个超时任务", timeoutTasks.size());

                for (VideoAnalysisTask task : timeoutTasks) {
                    handleTimeoutTask(task);
                }

                // 发送系统通知
                notificationService.sendSystemNotification(
                        String.format("处理了 %d 个超时任务", timeoutTasks.size()),
                        "TIMEOUT_HANDLING");
            }

        } catch (Exception e) {
            log.error("处理超时任务时发生错误", e);
            notificationService.sendSystemNotification(
                    "处理超时任务时发生错误: " + e.getMessage(),
                    "ERROR");
        }
    }

    /**
     * 重试失败的任务
     * 每10分钟执行一次
     */
    @Scheduled(fixedRate = 600000) // 10分钟
    public void retryFailedTasks() {
        if (!scheduleEnabled) {
            return;
        }

        try {
            log.debug("开始检查需要重试的任务...");

            List<VideoAnalysisTask> retryTasks = taskStatusService.getTasksForRetry(maxRetryAttempts);

            if (!retryTasks.isEmpty()) {
                log.info("发现 {} 个需要重试的任务", retryTasks.size());

                for (VideoAnalysisTask task : retryTasks) {
                    retryTask(task);
                }
            }

        } catch (Exception e) {
            log.error("重试失败任务时发生错误", e);
        }
    }

    /**
     * 清理历史数据
     * 每天凌晨2点执行一次
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupHistoryData() {
        if (!scheduleEnabled) {
            return;
        }

        try {
            log.info("开始清理历史数据，保留 {} 天内的数据", historyRetentionDays);

            int cleanedCount = taskStatusService.cleanupOldTasks(historyRetentionDays);

            if (cleanedCount > 0) {
                log.info("清理了 {} 个历史任务", cleanedCount);
                notificationService.sendSystemNotification(
                        String.format("清理了 %d 个历史任务", cleanedCount),
                        "DATA_CLEANUP");
            } else {
                log.info("没有需要清理的历史数据");
            }

        } catch (Exception e) {
            log.error("清理历史数据时发生错误", e);
            notificationService.sendSystemNotification(
                    "清理历史数据失败: " + e.getMessage(),
                    "ERROR");
        }
    }

    /**
     * 检查AI服务健康状态
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void checkAIServiceHealth() {
        if (!scheduleEnabled) {
            return;
        }

        try {
            boolean isHealthy = aiIntegrationService.checkAIServiceHealth();

            if (!isHealthy) {
                log.warn("AI服务健康检查失败");
                notificationService.sendSystemNotification(
                        "AI服务不可用，请检查AI模型服务状态",
                        "AI_SERVICE_DOWN");
            } else {
                log.debug("AI服务健康检查正常");
            }

        } catch (Exception e) {
            log.error("AI服务健康检查时发生错误", e);
        }
    }

    /**
     * 发送任务统计报告
     * 每小时执行一次
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void sendTaskStatisticsReport() {
        if (!scheduleEnabled) {
            return;
        }

        try {
            log.debug("生成任务统计报告...");

            var statistics = taskStatusService.getTaskStatistics();

            // 构建统计消息
            StringBuilder report = new StringBuilder();
            report.append("任务统计报告 - ");
            report.append("活跃任务: ").append(statistics.get("activeTasksCount"));
            report.append(", 已完成: ").append(statistics.get("completedTasksCount"));
            report.append(", 失败: ").append(statistics.get("failedTasksCount"));

            // 发送统计通知
            notificationService.sendSystemNotification(
                    report.toString(),
                    "STATISTICS_REPORT");

            log.info(report.toString());

        } catch (Exception e) {
            log.error("生成任务统计报告时发生错误", e);
        }
    }

    /**
     * 处理单个超时任务
     */
    private void handleTimeoutTask(VideoAnalysisTask task) {
        log.warn("处理超时任务 - 任务ID: {}, AI开始时间: {}",
                task.getTaskId(), task.getAiStartedAt());

        // 检查是否可以重试
        if (task.canRetry(maxRetryAttempts)) {
            // 标记为失败并准备重试
            taskStatusService.updateTaskStatus(task.getTaskId(),
                    VideoAnalysisTask.TaskStatus.AI_FAILED,
                    "AI处理超时 (超过 " + aiProcessingTimeoutMinutes + " 分钟)");

            log.info("超时任务将在下次调度中重试 - 任务ID: {}", task.getTaskId());
        } else {
            // 超过最大重试次数，标记为最终失败
            taskStatusService.updateTaskStatus(task.getTaskId(),
                    VideoAnalysisTask.TaskStatus.FAILED,
                    "AI处理超时且已达到最大重试次数");

            // 发送错误通知
            notificationService.sendErrorNotification(task.getTaskId(),
                    "TIMEOUT_FINAL_FAILURE",
                    "任务超时且超过最大重试次数");

            log.error("超时任务最终失败 - 任务ID: {}, 重试次数: {}",
                    task.getTaskId(), task.getRetryCount());
        }
    }

    /**
     * 重试单个失败任务
     */
    private void retryTask(VideoAnalysisTask task) {
        log.info("开始重试任务 - 任务ID: {}, 重试次数: {}",
                task.getTaskId(), task.getRetryCount() + 1);

        try {
            // 增加重试次数
            taskStatusService.incrementRetryCount(task.getTaskId());

            // 重新发送给AI分析
            aiIntegrationService.analyzeVideoAsync(
                    task.getTaskId(),
                    task.getVideoUrl(),
                    task.getIntersectionId(),
                    task.getDirection().name());

            log.info("任务重试已发起 - 任务ID: {}", task.getTaskId());

        } catch (Exception e) {
            log.error("任务重试失败 - 任务ID: {}", task.getTaskId(), e);

            // 重试失败，检查是否达到最大重试次数
            if (task.getRetryCount() + 1 >= maxRetryAttempts) {
                taskStatusService.updateTaskStatus(task.getTaskId(),
                        VideoAnalysisTask.TaskStatus.FAILED,
                        "重试失败且已达到最大重试次数: " + e.getMessage());

                notificationService.sendErrorNotification(task.getTaskId(),
                        "RETRY_FINAL_FAILURE",
                        "任务重试失败且超过最大重试次数");
            }
        }
    }

    /**
     * 获取调度服务状态
     */
    public java.util.Map<String, Object> getScheduleServiceStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        status.put("enabled", scheduleEnabled);
        status.put("aiProcessingTimeoutMinutes", aiProcessingTimeoutMinutes);
        status.put("maxRetryAttempts", maxRetryAttempts);
        status.put("historyRetentionDays", historyRetentionDays);
        status.put("aiServiceHealth", aiIntegrationService.checkAIServiceHealth());
        status.put("lastCheckedAt", java.time.LocalDateTime.now());

        return status;
    }

    /**
     * 手动触发超时任务处理（用于测试）
     */
    public void manualHandleTimeoutTasks() {
        log.info("手动触发超时任务处理");
        handleTimeoutTasks();
    }

    /**
     * 手动触发失败任务重试（用于测试）
     */
    public void manualRetryFailedTasks() {
        log.info("手动触发失败任务重试");
        retryFailedTasks();
    }
}