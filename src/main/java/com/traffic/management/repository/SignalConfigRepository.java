package com.traffic.management.repository;

import com.traffic.management.entity.SignalConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 信号灯配置Repository
 */
@Repository
public interface SignalConfigRepository extends JpaRepository<SignalConfig, Long> {

    /**
     * 根据路口ID查询配置
     */
    Optional<SignalConfig> findByIntersectionId(Long intersectionId);

    /**
     * 检查路口是否已有配置
     */
    boolean existsByIntersectionId(Long intersectionId);

    /**
     * 根据信号灯模式查询
     */
    List<SignalConfig> findBySignalMode(SignalConfig.SignalMode signalMode);
}
