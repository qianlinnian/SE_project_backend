package com.traffic.management.repository;

import com.traffic.management.entity.SignalLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 信号灯日志Repository
 */
@Repository
public interface SignalLogRepository extends JpaRepository<SignalLog, Long> {

    /**
     * 根据路口ID分页查询日志
     */
    Page<SignalLog> findByIntersectionId(Long intersectionId, Pageable pageable);

    /**
     * 根据操作类型查询
     */
    List<SignalLog> findByActionType(String actionType);

    /**
     * 根据时间范围查询
     */
    List<SignalLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 根据路口ID和时间范围查询
     */
    Page<SignalLog> findByIntersectionIdAndCreatedAtBetween(
            Long intersectionId,
            LocalDateTime start,
            LocalDateTime end,
            Pageable pageable
    );
}
