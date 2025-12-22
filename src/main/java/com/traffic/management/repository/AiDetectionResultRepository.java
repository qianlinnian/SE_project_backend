package com.traffic.management.repository;

import com.traffic.management.entity.AiDetectionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AI检测结果Repository
 */
@Repository
public interface AiDetectionResultRepository extends JpaRepository<AiDetectionResult, Long> {

    /**
     * 根据结果ID查询
     */
    Optional<AiDetectionResult> findByResultId(String resultId);

    /**
     * 查询指定任务的所有检测结果
     */
    List<AiDetectionResult> findByTaskId(String taskId);

    /**
     * 查询指定任务的待处理结果
     */
    List<AiDetectionResult> findByTaskIdAndProcessingStatus(
            String taskId,
            AiDetectionResult.ProcessingStatus status);

    /**
     * 查询指定任务的已验证结果
     */
    List<AiDetectionResult> findByTaskIdAndProcessingStatusOrderByDetectionTimestampDesc(
            String taskId,
            AiDetectionResult.ProcessingStatus status);

    /**
     * 统计任务的各状态结果数量
     */
    @Query("SELECT r.processingStatus, COUNT(r) FROM AiDetectionResult r WHERE r.taskId = :taskId GROUP BY r.processingStatus")
    List<Object[]> countByProcessingStatusForTask(@Param("taskId") String taskId);

    /**
     * 查询需要处理的结果（按时间排序）
     */
    List<AiDetectionResult> findByProcessingStatusOrderByDetectionTimestampAsc(
            AiDetectionResult.ProcessingStatus status);

    /**
     * 检查车牌号重复检测（去重用）
     */
    @Query("SELECT COUNT(r) FROM AiDetectionResult r WHERE r.taskId = :taskId AND r.plateNumber = :plateNumber AND r.processingStatus != 'REJECTED'")
    long countByTaskIdAndPlateNumberExcludingRejected(
            @Param("taskId") String taskId,
            @Param("plateNumber") String plateNumber);

    /**
     * 查询指定时间范围内的检测结果
     */
    List<AiDetectionResult> findByDetectionTimestampBetween(
            LocalDateTime startTime,
            LocalDateTime endTime);

    /**
     * 删除指定任务的所有检测结果
     */
    void deleteByTaskId(String taskId);

    /**
     * 查询高置信度的未处理结果
     */
    @Query("SELECT r FROM AiDetectionResult r WHERE r.processingStatus = 'PENDING' AND r.confidence >= :minConfidence ORDER BY r.confidence DESC")
    List<AiDetectionResult> findHighConfidencePendingResults(@Param("minConfidence") Double minConfidence);
}