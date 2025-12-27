package com.traffic.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 交通流历史记录表 - 全量存储
 */
@Entity
@Table(name = "traffic_flow_records", indexes = {
    @Index(name = "idx_traffic_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficFlowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 仿真时间戳 (秒)
    @Column(nullable = false)
    private Double simulationTimestamp;

    // 仿真步数
    private Integer step;

    // 控制模式
    @Column(length = 20)
    private String controlMode;

    // 全局统计
    private Integer totalQueue;
    private Integer totalVehicles;

    // 存储完整的路口数据快照 (JSON格式)
    // 包含 intersections 数组的所有内容
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> fullDataSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}