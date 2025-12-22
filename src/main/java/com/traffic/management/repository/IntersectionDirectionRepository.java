package com.traffic.management.repository;

import com.traffic.management.entity.IntersectionDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IntersectionDirectionRepository extends JpaRepository<IntersectionDirection, Long> {

    /**
     * 根据路口ID查找所有方向
     */
    List<IntersectionDirection> findByIntersectionId(Long intersectionId);

    /**
     * 根据路口ID和方向查找配置
     */
    Optional<IntersectionDirection> findByIntersectionIdAndDirection(Long intersectionId,
            IntersectionDirection.Direction direction);

    /**
     * 根据摄像头ID查找方向配置
     */
    Optional<IntersectionDirection> findByCameraId(String cameraId);

    /**
     * 根据检测器ID查找方向配置
     */
    Optional<IntersectionDirection> findByDetectorId(String detectorId);

    /**
     * 查询指定路口的高优先级方向
     */
    @Query("SELECT id FROM IntersectionDirection id WHERE id.intersectionId = :intersectionId AND id.priorityLevel >= :minPriority ORDER BY id.priorityLevel DESC")
    List<IntersectionDirection> findHighPriorityDirections(Long intersectionId, Integer minPriority);

    /**
     * 查询所有启用的方向配置
     */
    @Query("SELECT id FROM IntersectionDirection id WHERE id.priorityLevel > 0 ORDER BY id.intersectionId, id.direction")
    List<IntersectionDirection> findAllActiveDirections();
}