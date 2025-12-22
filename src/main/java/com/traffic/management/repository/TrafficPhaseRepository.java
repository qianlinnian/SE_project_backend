package com.traffic.management.repository;

import com.traffic.management.entity.TrafficPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrafficPhaseRepository extends JpaRepository<TrafficPhase, Long> {

    /**
     * 根据路口ID查找所有相位配置
     */
    List<TrafficPhase> findByIntersectionIdOrderByPhaseSequence(Long intersectionId);

    /**
     * 根据路口ID和相位序号查找配置
     */
    Optional<TrafficPhase> findByIntersectionIdAndPhaseSequence(Long intersectionId, Integer phaseSequence);

    /**
     * 查找指定路口的活跃相位
     */
    @Query("SELECT tp FROM TrafficPhase tp WHERE tp.intersectionId = :intersectionId AND tp.isActive = true ORDER BY tp.phaseSequence")
    List<TrafficPhase> findActivePhasesByIntersectionId(Long intersectionId);

    /**
     * 查找当前正在执行的相位
     */
    @Query("SELECT tp FROM TrafficPhase tp WHERE tp.intersectionId = :intersectionId AND tp.currentStatus IN ('GREEN', 'YELLOW')")
    Optional<TrafficPhase> findCurrentActivePhase(Long intersectionId);

    /**
     * 查找下一个待执行的相位
     */
    @Query("SELECT tp FROM TrafficPhase tp WHERE tp.intersectionId = :intersectionId AND tp.isActive = true AND tp.phaseSequence > :currentSequence ORDER BY tp.phaseSequence LIMIT 1")
    Optional<TrafficPhase> findNextPhase(Long intersectionId, Integer currentSequence);

    /**
     * 查找第一个相位（用于循环）
     */
    @Query("SELECT tp FROM TrafficPhase tp WHERE tp.intersectionId = :intersectionId AND tp.isActive = true ORDER BY tp.phaseSequence LIMIT 1")
    Optional<TrafficPhase> findFirstPhase(Long intersectionId);
}