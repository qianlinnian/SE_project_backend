package com.traffic.management.service;

import com.traffic.management.entity.VideoAnalysisTask;
import com.traffic.management.repository.VideoAnalysisTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 任务状态管理服务
 * 负责视频分析任务的状态跟踪和管理
 */
@Slf4j
@Service
@Transactional
public class TaskStatusService {

    @Autowired
    private VideoAnalysisTaskRepository taskRepository;

    @Autowired
    private NotificationService notificationService;

    /**
     * 创建新任务
     */
    public VideoAnalysisTask createTask(String taskId, Long intersectionId,
            VideoAnalysisTask.Direction direction,
            String videoFilePath, String videoUrl,
            String originalFilename, Long fileSize) {
        log.info("创建新视频分析任务 - 任务ID: {}, 路口: {}, 方向: {}", taskId, intersectionId, direction);

        VideoAnalysisTask task = VideoAnalysisTask.builder()
                .taskId(taskId)
                .intersectionId(intersectionId)
                .direction(direction)
                .videoFilePath(videoFilePath)
                .videoUrl(videoUrl)
                .originalFilename(originalFilename)
                .fileSize(fileSize)
                .status(VideoAnalysisTask.TaskStatus.UPLOADED)
                .progress(10)
                .createdAt(LocalDateTime.now())
                .build();

        task = taskRepository.save(task);

        // 发送通知
        notificationService.sendTaskCreatedNotification(task);

        return task;
    }

    /**
     * 更新任务状态
     */
    public void updateTaskStatus(String taskId, VideoAnalysisTask.TaskStatus newStatus, String errorMessage) {
        Optional<VideoAnalysisTask> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            VideoAnalysisTask task = taskOpt.get();
            VideoAnalysisTask.TaskStatus oldStatus = task.getStatus();

            task.updateStatus(newStatus, errorMessage);
            taskRepository.save(task);

            log.info("任务状态更新 - 任务ID: {}, {} -> {}, 进度: {}%",
                    taskId, oldStatus, newStatus, task.getProgress());

            // 发送状态变化通知
            notificationService.sendTaskStatusChangeNotification(task, oldStatus);
        } else {
            log.warn("尝试更新不存在的任务状态 - 任务ID: {}", taskId);
        }
    }

    /**
     * 更新任务进度
     */
    public void updateTaskProgress(String taskId, int progress) {
        Optional<VideoAnalysisTask> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            VideoAnalysisTask task = taskOpt.get();
            task.setProgress(progress);
            taskRepository.save(task);

            log.debug("任务进度更新 - 任务ID: {}, 进度: {}%", taskId, progress);

            // 发送进度通知
            notificationService.sendTaskProgressNotification(task);
        }
    }

    /**
     * 增加重试次数
     */
    public void incrementRetryCount(String taskId) {
        Optional<VideoAnalysisTask> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            VideoAnalysisTask task = taskOpt.get();
            task.incrementRetryCount();
            taskRepository.save(task);

            log.info("任务重试次数增加 - 任务ID: {}, 重试次数: {}", taskId, task.getRetryCount());
        }
    }

    /**
     * 更新AI请求信息
     */
    public void updateAIRequestInfo(String taskId, String requestPayload, String callbackUrl) {
        Optional<VideoAnalysisTask> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            VideoAnalysisTask task = taskOpt.get();
            task.setAiRequestPayload(requestPayload);
            task.setAiCallbackUrl(callbackUrl);
            taskRepository.save(task);

            log.debug("AI请求信息已更新 - 任务ID: {}", taskId);
        }
    }

    /**
     * 更新违章统计
     */
    public void updateViolationStats(String taskId, int totalDetected, int processedCount) {
        Optional<VideoAnalysisTask> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            VideoAnalysisTask task = taskOpt.get();
            task.setTotalViolations(totalDetected);
            task.setProcessedViolations(processedCount);
            taskRepository.save(task);

            log.info("违章统计更新 - 任务ID: {}, 检测到: {}, 已处理: {}",
                    taskId, totalDetected, processedCount);
        }
    }

    /**
     * 获取任务详情
     */
    public Optional<VideoAnalysisTask> getTaskDetails(String taskId) {
        return taskRepository.findByTaskId(taskId);
    }

    /**
     * 获取任务状态信息
     */
    public Map<String, Object> getTaskStatusInfo(String taskId) {
        Optional<VideoAnalysisTask> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            VideoAnalysisTask task = taskOpt.get();
            return buildTaskStatusInfo(task);
        } else {
            return Map.of("error", "任务不存在", "taskId", taskId);
        }
    }

    /**
     * 构建任务状态信息
     */
    private Map<String, Object> buildTaskStatusInfo(VideoAnalysisTask task) {
        Map<String, Object> info = new HashMap<>();
        info.put("taskId", task.getTaskId());
        info.put("status", task.getStatus());
        info.put("statusDescription", task.getStatus().getDescription());
        info.put("progress", task.getProgress());
        info.put("intersectionId", task.getIntersectionId());
        info.put("direction", task.getDirection());
        info.put("directionName", task.getDirection().getChineseName());
        info.put("originalFilename", task.getOriginalFilename());
        info.put("fileSize", task.getFileSize());
        info.put("createdAt", task.getCreatedAt());
        info.put("aiStartedAt", task.getAiStartedAt());
        info.put("aiCompletedAt", task.getAiCompletedAt());
        info.put("completedAt", task.getCompletedAt());
        info.put("retryCount", task.getRetryCount());
        info.put("totalViolations", task.getTotalViolations());
        info.put("processedViolations", task.getProcessedViolations());
        info.put("executionDuration", task.getExecutionDurationSeconds());
        info.put("aiProcessingDuration", task.getAiProcessingDurationSeconds());

        if (task.getAiErrorMessage() != null) {
            info.put("errorMessage", task.getAiErrorMessage());
        }

        return info;
    }

    /**
     * 获取路口任务列表
     */
    public Page<VideoAnalysisTask> getIntersectionTasks(Long intersectionId, Pageable pageable) {
        return taskRepository.findByIntersectionId(intersectionId, pageable);
    }

    /**
     * 获取指定方向的任务列表
     */
    public Page<VideoAnalysisTask> getDirectionTasks(Long intersectionId,
            VideoAnalysisTask.Direction direction,
            Pageable pageable) {
        return taskRepository.findByIntersectionIdAndDirection(intersectionId, direction, pageable);
    }

    /**
     * 获取最近的任务列表
     */
    public List<VideoAnalysisTask> getRecentTasks() {
        return taskRepository.findTop10ByOrderByCreatedAtDesc();
    }

    /**
     * 获取需要重试的任务
     */
    public List<VideoAnalysisTask> getTasksForRetry(int maxRetryCount) {
        return taskRepository.findTasksForRetry(maxRetryCount);
    }

    /**
     * 获取超时的AI处理任务
     */
    public List<VideoAnalysisTask> getTimeoutAiTasks(int timeoutMinutes) {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        return taskRepository.findTimeoutAiTasks(timeoutThreshold);
    }

    /**
     * 获取任务统计信息
     */
    public Map<String, Object> getTaskStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // 按状态统计
        List<Object[]> statusCounts = taskRepository.countByStatus();
        Map<String, Long> statusStats = statusCounts.stream()
                .collect(Collectors.toMap(
                        row -> ((VideoAnalysisTask.TaskStatus) row[0]).getDescription(),
                        row -> (Long) row[1]));
        stats.put("statusDistribution", statusStats);

        // 最近任务
        List<VideoAnalysisTask> recentTasks = getRecentTasks();
        stats.put("recentTasksCount", recentTasks.size());
        stats.put("recentTasks", recentTasks.stream()
                .map(this::buildTaskStatusInfo)
                .collect(Collectors.toList()));

        // 系统状态
        stats.put("activeTasksCount", statusStats.getOrDefault("AI分析中", 0L));
        stats.put("completedTasksCount", statusStats.getOrDefault("验证完成", 0L));
        stats.put("failedTasksCount", statusStats.getOrDefault("任务失败", 0L) +
                statusStats.getOrDefault("AI分析失败", 0L));

        return stats;
    }

    /**
     * 获取路口方向统计
     */
    public Map<String, Long> getIntersectionDirectionStats(Long intersectionId) {
        List<Object[]> directionCounts = taskRepository.countByDirectionForIntersection(intersectionId);
        return directionCounts.stream()
                .collect(Collectors.toMap(
                        row -> ((VideoAnalysisTask.Direction) row[0]).getChineseName(),
                        row -> (Long) row[1]));
    }

    /**
     * 处理超时任务
     */
    public void handleTimeoutTasks(int timeoutMinutes) {
        List<VideoAnalysisTask> timeoutTasks = getTimeoutAiTasks(timeoutMinutes);
        for (VideoAnalysisTask task : timeoutTasks) {
            log.warn("检测到超时任务 - 任务ID: {}, AI开始时间: {}", task.getTaskId(), task.getAiStartedAt());

            updateTaskStatus(task.getTaskId(), VideoAnalysisTask.TaskStatus.AI_FAILED,
                    "AI处理超时 (超过 " + timeoutMinutes + " 分钟)");
        }
    }

    /**
     * 清理历史任务
     */
    public int cleanupOldTasks(int daysOld) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(daysOld);
        return taskRepository.deleteCompletedTasksBefore(cutoffTime);
    }

    /**
     * 任务完成处理
     */
    public void completeTask(String taskId) {
        updateTaskStatus(taskId, VideoAnalysisTask.TaskStatus.VALIDATION_COMPLETED, null);

        Optional<VideoAnalysisTask> taskOpt = taskRepository.findByTaskId(taskId);
        if (taskOpt.isPresent()) {
            VideoAnalysisTask task = taskOpt.get();
            notificationService.sendTaskCompletedNotification(task);

            log.info("任务完成 - 任务ID: {}, 总耗时: {}秒, 检测违章: {}",
                    taskId, task.getExecutionDurationSeconds(), task.getTotalViolations());
        }
    }
}