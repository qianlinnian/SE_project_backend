package com.traffic.management.repository;

import com.traffic.management.entity.Violation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * 按违规类型查询（分页）
     */
    Page<Violation> findByViolationType(Violation.ViolationType violationType, Pageable pageable);

    /**
     * 按车牌号模糊查询（分页）
     */
    Page<Violation> findByPlateNumberContaining(String plateNumber, Pageable pageable);

    /**
     * 按违规类型和车牌号模糊查询（分页）
     */
    Page<Violation> findByViolationTypeAndPlateNumberContaining(
            Violation.ViolationType violationType,
            String plateNumber,
            Pageable pageable);

    // ========== 统计分析相关查询 ==========

    /**
     * 按时间范围统计各状态的违章数量
     */
    Long countByStatusAndOccurredAtBetween(
            Violation.ViolationStatus status,
            LocalDateTime startTime,
            LocalDateTime endTime);

    /**
     * 按时间范围统计总数
     */
    Long countByOccurredAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按违规类型分组统计 (时间范围)
     */
    @Query("SELECT v.violationType as type, COUNT(v) as count " +
           "FROM Violation v " +
           "WHERE v.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY v.violationType")
    List<Object[]> countByViolationTypeGrouped(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * TOP违规车牌 (按违规次数降序)
     */
    @Query("SELECT v.plateNumber as plateNumber, " +
           "COUNT(v) as count, " +
           "v.violationType as mainType " +
           "FROM Violation v " +
           "WHERE v.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY v.plateNumber, v.violationType " +
           "ORDER BY count DESC")
    List<Object[]> findTopViolators(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * 按小时分组统计 (用于时间趋势)
     */
    @Query("SELECT FUNCTION('DATE_FORMAT', v.occurredAt, '%Y-%m-%d %H:00:00') as timeSlot, " +
           "COUNT(v) as count " +
           "FROM Violation v " +
           "WHERE v.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY FUNCTION('DATE_FORMAT', v.occurredAt, '%Y-%m-%d %H:00:00') " +
           "ORDER BY timeSlot")
    List<Object[]> countByHourGrouped(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 按天分组统计 (用于时间趋势)
     */
    @Query("SELECT FUNCTION('DATE', v.occurredAt) as date, " +
           "COUNT(v) as count " +
           "FROM Violation v " +
           "WHERE v.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY FUNCTION('DATE', v.occurredAt) " +
           "ORDER BY date")
    List<Object[]> countByDayGrouped(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 获取热力图数据 (小时 × 星期)
     */
    @Query("SELECT FUNCTION('HOUR', v.occurredAt) as hour, " +
           "FUNCTION('DAYOFWEEK', v.occurredAt) as dayOfWeek, " +
           "COUNT(v) as count " +
           "FROM Violation v " +
           "WHERE v.occurredAt BETWEEN :startTime AND :endTime " +
           "GROUP BY FUNCTION('HOUR', v.occurredAt), FUNCTION('DAYOFWEEK', v.occurredAt)")
    List<Object[]> getHeatmapData(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
