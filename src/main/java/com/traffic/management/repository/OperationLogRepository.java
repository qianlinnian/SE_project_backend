package com.traffic.management.repository;

import com.traffic.management.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 操作日志Repository
 */
@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    /**
     * 根据操作人ID分页查询日志
     */
    Page<OperationLog> findByOperatorId(Long operatorId, Pageable pageable);

    /**
     * 根据操作类型分页查询
     */
    Page<OperationLog> findByOperationType(String operationType, Pageable pageable);

    /**
     * 根据目标类型分页查询
     */
    Page<OperationLog> findByTargetType(String targetType, Pageable pageable);

    /**
     * 根据时间范围分页查询
     */
    Page<OperationLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * 根据操作人ID和时间范围查询
     */
    Page<OperationLog> findByOperatorIdAndCreatedAtBetween(
            Long operatorId,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );

    /**
     * 多条件组合查询
     */
    Page<OperationLog> findByOperatorIdAndOperationTypeAndCreatedAtBetween(
            Long operatorId,
            String operationType,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );
}
