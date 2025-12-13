package com.traffic.management.repository;

import com.traffic.management.entity.Intersection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IntersectionRepository extends JpaRepository<Intersection, Long> {

    /**
     * 按名称查询路口
     */
    Optional<Intersection> findByName(String name);

    /**
     * 按设备ID查询路口
     */
    Optional<Intersection> findByDeviceId(String deviceId);
}
