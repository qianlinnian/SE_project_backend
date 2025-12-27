package com.traffic.management.repository;

import com.traffic.management.entity.VideoAnalysisTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 视频分析任务Repository
 */
@Repository
public interface VideoAnalysisTaskRepository extends JpaRepository<VideoAnalysisTask, Long> {

    /**
     * 根据任务ID查询
     */
    Optional<VideoAnalysisTask> findByTaskId(String taskId);

    /**
     * 根据状态查询任务
     */
    List<VideoAnalysisTask> findByStatus(VideoAnalysisTask.TaskStatus status);

    /**
     * 查询指定路口的任务
     */
    Page<VideoAnalysisTask> findByIntersectionId(Long intersectionId, Pageable pageable);

    /**
     * 查询指定路口和方向的任务
     */
    Page<VideoAnalysisTask> findByIntersectionIdAndDirection(
            Long intersectionId,
            VideoAnalysisTask.Direction direction,
            Pageable pageable);

    /**
     * 查询指定时间范围内的任务
     */
    List<VideoAnalysisTask> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询需要重试的失败任务
     */
    @Query("SELECT t FROM VideoAnalysisTask t WHERE t.status IN ('AI_FAILED', 'FAILED') AND t.retryCount < :maxRetryCount")
    List<VideoAnalysisTask> findTasksForRetry(@Param("maxRetryCount") int maxRetryCount);

    /**
     * 查询超时的AI处理任务
     */
    @Query("SELECT t FROM VideoAnalysisTask t WHERE t.status = 'AI_PROCESSING' AND t.aiStartedAt < :timeoutThreshold")
    List<VideoAnalysisTask> findTimeoutAiTasks(@Param("timeoutThreshold") LocalDateTime timeoutThreshold);

    /**
     * 统计各状态任务数量
     */
    @Query("SELECT t.status, COUNT(t) FROM VideoAnalysisTask t GROUP BY t.status")
    List<Object[]> countByStatus();

    /**
     * 统计指定路口各方向任务数量
     */
    @Query("SELECT t.direction, COUNT(t) FROM VideoAnalysisTask t WHERE t.intersectionId = :intersectionId GROUP BY t.direction")
    List<Object[]> countByDirectionForIntersection(@Param("intersectionId") Long intersectionId);

    /**
     * 查询最近的任务（用于监控）
     */
    List<VideoAnalysisTask> findTop10ByOrderByCreatedAtDesc();

    /**
     * 删除指定时间之前的已完成任务（清理历史数据）
     */
    @Query("DELETE FROM VideoAnalysisTask t WHERE t.status IN ('VALIDATION_COMPLETED', 'FAILED') AND t.completedAt < :beforeTime")
    int deleteCompletedTasksBefore(@Param("beforeTime") LocalDateTime beforeTime);
}