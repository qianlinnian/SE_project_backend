package com.traffic.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 视频分析任务实体
 * 用于追踪视频上传到违章检测完成的整个流程
 */
@Entity
@Table(name = "video_analysis_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoAnalysisTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", unique = true, nullable = false, length = 36)
    private String taskId;

    @Column(name = "intersection_id", nullable = false)
    private Long intersectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private Direction direction;

    @Column(name = "video_file_path", nullable = false, length = 500)
    private String videoFilePath;

    @Column(name = "video_url", nullable = false, length = 500)
    private String videoUrl;

    // 任务状态管理
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TaskStatus status = TaskStatus.UPLOADED;

    @Column(name = "progress")
    @Builder.Default
    private Integer progress = 0;

    // 时间戳
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "ai_started_at")
    private LocalDateTime aiStartedAt;

    @Column(name = "ai_completed_at")
    private LocalDateTime aiCompletedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // AI相关信息
    @Column(name = "ai_callback_url", length = 500)
    private String aiCallbackUrl;

    @Column(name = "ai_request_payload", columnDefinition = "TEXT")
    private String aiRequestPayload;

    @Column(name = "ai_error_message", columnDefinition = "TEXT")
    private String aiErrorMessage;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    // 文件信息
    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // 结果统计
    @Column(name = "total_violations")
    @Builder.Default
    private Integer totalViolations = 0;

    @Column(name = "processed_violations")
    @Builder.Default
    private Integer processedViolations = 0;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        UPLOADED("已上传"),
        AI_PROCESSING("AI分析中"),
        AI_COMPLETED("AI分析完成"),
        AI_FAILED("AI分析失败"),
        VALIDATION_COMPLETED("验证完成"),
        FAILED("任务失败");

        private final String description;

        TaskStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean isTerminalState() {
            return this == VALIDATION_COMPLETED || this == FAILED;
        }

        public boolean isFailureState() {
            return this == AI_FAILED || this == FAILED;
        }
    }

    /**
     * 方向枚举（复用Violation中的Direction）
     */
    public enum Direction {
        EAST("东向"),
        SOUTH("南向"),
        WEST("西向"),
        NORTH("北向");

        private final String chineseName;

        Direction(String chineseName) {
            this.chineseName = chineseName;
        }

        public String getChineseName() {
            return chineseName;
        }
    }

    /**
     * 计算处理进度百分比
     */
    public Integer calculateProgress() {
        if (status == TaskStatus.UPLOADED)
            return 10;
        if (status == TaskStatus.AI_PROCESSING)
            return 50;
        if (status == TaskStatus.AI_COMPLETED)
            return 80;
        if (status == TaskStatus.VALIDATION_COMPLETED)
            return 100;
        if (status == TaskStatus.AI_FAILED || status == TaskStatus.FAILED)
            return progress;
        return progress;
    }

    /**
     * 检查是否可以重试
     */
    public boolean canRetry(int maxRetryCount) {
        return status.isFailureState() && retryCount < maxRetryCount;
    }

    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }

    /**
     * 更新任务状态和进度
     */
    public void updateStatus(TaskStatus newStatus, String errorMessage) {
        this.status = newStatus;
        this.progress = calculateProgress();

        switch (newStatus) {
            case AI_PROCESSING -> this.aiStartedAt = LocalDateTime.now();
            case AI_COMPLETED -> this.aiCompletedAt = LocalDateTime.now();
            case VALIDATION_COMPLETED -> this.completedAt = LocalDateTime.now();
            case AI_FAILED, FAILED -> this.aiErrorMessage = errorMessage;
        }
    }

    /**
     * 获取任务执行时长（秒）
     */
    public Long getExecutionDurationSeconds() {
        if (completedAt != null && createdAt != null) {
            return java.time.Duration.between(createdAt, completedAt).getSeconds();
        }
        return null;
    }

    /**
     * 获取AI处理时长（秒）
     */
    public Long getAiProcessingDurationSeconds() {
        if (aiCompletedAt != null && aiStartedAt != null) {
            return java.time.Duration.between(aiStartedAt, aiCompletedAt).getSeconds();
        }
        return null;
    }
}