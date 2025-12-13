package com.traffic.management.repository;

import com.traffic.management.entity.Violation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ViolationRepository extends JpaRepository<Violation, Long> {

    /**
     * 按车牌号查询违章记录
     */
    List<Violation> findByPlateNumber(String plateNumber);

    /**
     * 按状态查询违章记录
     */
    Page<Violation> findByStatus(Violation.ViolationStatus status, Pageable pageable);

    /**
     * 按时间范围查询违章记录
     */
    List<Violation> findByOccurredAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按路口ID和时间范围查询违章
     */
    List<Violation> findByIntersectionIdAndOccurredAtBetween(
            Long intersectionId,
            LocalDateTime startTime,
            LocalDateTime endTime);

    /**
     * 统计待审核的违章数量
     */
    Long countByStatus(Violation.ViolationStatus status);

    /**
     * 分页查询所有违章
     */
    Page<Violation> findAll(Pageable pageable);
}
