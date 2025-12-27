package com.traffic.management.repository;

import com.traffic.management.entity.TrafficFlowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TrafficFlowRecordRepository extends JpaRepository<TrafficFlowRecord, Long> {
    
    // 根据时间范围查询历史记录，用于图表展示
    List<TrafficFlowRecord> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime startTime, LocalDateTime endTime);
    
    // 获取最近的N条记录
    List<TrafficFlowRecord> findTop100ByOrderByCreatedAtDesc();
}