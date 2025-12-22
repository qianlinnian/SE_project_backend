package com.traffic.management.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 视频分析任务状态管理服务
 */
@Service
public class VideoTaskStatusService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TASK_STATUS_PREFIX = "video:task:";
    private static final long TASK_EXPIRATION_HOURS = 24; // 任务状态保存24小时

    /**
     * 更新任务状态
     */
    public void updateTaskStatus(String taskId, String status, Map<String, Object> details) {
        String key = TASK_STATUS_PREFIX + taskId;

        Map<String, Object> taskInfo = new HashMap<>();
        taskInfo.put("taskId", taskId);
        taskInfo.put("status", status);
        taskInfo.put("updatedAt", System.currentTimeMillis());
        taskInfo.putAll(details);

        redisTemplate.opsForValue().set(key, taskInfo, TASK_EXPIRATION_HOURS, TimeUnit.HOURS);
    }

    /**
     * 获取任务状态
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        String key = TASK_STATUS_PREFIX + taskId;
        Object taskInfo = redisTemplate.opsForValue().get(key);

        if (taskInfo instanceof Map) {
            return (Map<String, Object>) taskInfo;
        }

        return Map.of(
                "taskId", taskId,
                "status", "NOT_FOUND",
                "message", "任务不存在或已过期");
    }

    /**
     * 删除任务状态
     */
    public void deleteTaskStatus(String taskId) {
        String key = TASK_STATUS_PREFIX + taskId;
        redisTemplate.delete(key);
    }

    /**
     * 设置任务为处理中状态
     */
    public void setTaskProcessing(String taskId, String message) {
        Map<String, Object> details = Map.of("message", message);
        updateTaskStatus(taskId, "PROCESSING", details);
    }

    /**
     * 设置任务为完成状态
     */
    public void setTaskCompleted(String taskId, Map<String, Object> result) {
        updateTaskStatus(taskId, "COMPLETED", result);
    }

    /**
     * 设置任务为失败状态
     */
    public void setTaskFailed(String taskId, String errorMessage) {
        Map<String, Object> details = Map.of(
                "message", "任务执行失败",
                "error", errorMessage);
        updateTaskStatus(taskId, "FAILED", details);
    }
}