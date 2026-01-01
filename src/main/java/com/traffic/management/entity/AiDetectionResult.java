package com.traffic.management.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI检测结果实体
 * 存储AI模型返回的原始检测数据
 */
@Entity
@Table(name = "ai_detection_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiDetectionResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, length = 36)
    private String taskId;

    @Column(name = "result_id", unique = true, nullable = false, length = 36)
    private String resultId;

    // AI原始结果
    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    /**
     * 车辆类型 (car, bus, truck, bicycle, motorcycle, train)
     */
    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

    @Column(name = "violation_type", nullable = false, length = 50)
    private String violationType;

    @Column(name = "confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    // 位置和时间信息
    @Column(name = "bounding_box", columnDefinition = "JSON")
    private String boundingBox;

    @Column(name = "frame_timestamp", precision = 10, scale = 3)
    private BigDecimal frameTimestamp;

    @Column(name = "detection_timestamp")
    private LocalDateTime detectionTimestamp;

    // 处理状态
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status")
    @Builder.Default
    private ProcessingStatus processingStatus = ProcessingStatus.PENDING;

    @Column(name = "violation_id")
    private Long violationId;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (detectionTimestamp == null) {
            detectionTimestamp = LocalDateTime.now();
        }
        if (resultId == null) {
            resultId = java.util.UUID.randomUUID().toString();
        }
    }

    /**
     * 处理状态枚举
     */
    public enum ProcessingStatus {
        PENDING("待处理"),
        VALIDATED("已验证"),
        REJECTED("已拒绝"),
        FAILED("处理失败");

        private final String description;

        ProcessingStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean isProcessed() {
            return this == VALIDATED || this == REJECTED;
        }
    }

    /**
     * 标记为已验证（转换为违章记录）
     */
    public void markAsValidated(Long violationId) {
        this.processingStatus = ProcessingStatus.VALIDATED;
        this.violationId = violationId;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 标记为已拒绝（不构成违章）
     */
    public void markAsRejected(String reason) {
        this.processingStatus = ProcessingStatus.REJECTED;
        this.rejectionReason = reason;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 标记为处理失败
     */
    public void markAsFailed(String reason) {
        this.processingStatus = ProcessingStatus.FAILED;
        this.rejectionReason = reason;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 获取置信度百分比
     */
    public double getConfidencePercent() {
        return confidence.doubleValue() * 100;
    }

    /**
     * 检查置信度是否达到阈值
     */
    public boolean meetsConfidenceThreshold(double threshold) {
        return confidence.doubleValue() >= threshold;
    }
}