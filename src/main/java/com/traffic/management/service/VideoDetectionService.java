package com.traffic.management.service;

import com.traffic.management.entity.Violation;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频检测服务 - 处理视频违章检测相关业务
 */
@Service
public class VideoDetectionService {

    @Autowired
    private ViolationService violationService;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private TrafficLightStateService trafficLightStateService;

    @Autowired
    private MultiDirectionTrafficLightService multiDirectionTrafficLightService;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    /**
     * 上传违章证据图片到MinIO
     */
    public String uploadViolationImage(MultipartFile imageFile, Long intersectionId) throws Exception {
        // 生成文件名：intersection_{id}/violation_{timestamp}.jpg
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String fileName = String.format("intersection_%d/violation_%s.jpg", intersectionId, timestamp);

        // 上传到MinIO
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(imageFile.getInputStream(), imageFile.getSize(), -1)
                        .contentType(imageFile.getContentType())
                        .build());

        // 返回可访问的URL
        return String.format("%s/%s/%s", minioEndpoint, bucketName, fileName);
    }

    /**
     * 上传视频文件到MinIO
     */
    public String uploadVideoFile(MultipartFile videoFile, Long intersectionId) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String fileName = String.format("intersection_%d/video_%s.mp4", intersectionId, timestamp);

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(videoFile.getInputStream(), videoFile.getSize(), -1)
                        .contentType(videoFile.getContentType())
                        .build());

        return String.format("%s/%s/%s", minioEndpoint, bucketName, fileName);
    }

    /**
     * 处理视频检测结果
     * 将大模型的检测结果转换为系统可识别的违章记录
     * 集成红绿灯状态检测
     */
    public Violation processDetectionResult(Map<String, Object> detectionResult) throws Exception {
        // 标准化检测结果数据格式
        Map<String, Object> violationData = new HashMap<>();

        // 基础信息
        Long intersectionId = Long.parseLong(detectionResult.get("intersectionId").toString());
        violationData.put("intersectionId", intersectionId);
        violationData.put("plateNumber", detectionResult.get("plateNumber"));
        violationData.put("imageUrl", detectionResult.get("imageUrl"));
        violationData.put("aiConfidence", detectionResult.get("confidence"));

        // 违章类型映射
        String detectedType = detectionResult.get("violationType").toString();
        String standardType = mapViolationType(detectedType);

        // 时间戳处理
        LocalDateTime violationTime = LocalDateTime.now();
        if (detectionResult.containsKey("timestamp")) {
            Long timestamp = Long.parseLong(detectionResult.get("timestamp").toString());
            violationTime = java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }

        // 获取方向和转弯类型信息（从检测结果中解析或使用默认值）
        Violation.Direction direction = parseDirection(detectionResult);
        Violation.TurnType turnType = parseTurnType(detectionResult, standardType);

        // 多方向红绿灯状态检测 - 关键逻辑
        boolean isViolation = multiDirectionTrafficLightService.validateViolationWithMultiDirectionLight(
                intersectionId, direction, turnType,
                Violation.ViolationType.valueOf(standardType), violationTime);

        if (!isViolation) {
            // 不构成违章，抛出异常或返回null
            throw new IllegalArgumentException(String.format("在当前红绿灯状态下不构成违章: %s (%s方向%s)",
                    standardType, direction.getChineseName(), turnType.getChineseName()));
        }

        // 在违章数据中添加方向和转弯类型信息
        violationData.put("direction", direction.name());
        violationData.put("turnType", turnType.name());

        violationData.put("violationType", standardType);
        violationData.put("occurredAt", violationTime);

        // 调用违章服务保存记录
        return violationService.reportViolation(violationData);
    }

    /**
     * 从检测结果中解析方向信息
     */
    private Violation.Direction parseDirection(Map<String, Object> detectionResult) {
        if (detectionResult.containsKey("direction")) {
            try {
                String directionStr = detectionResult.get("direction").toString().toUpperCase();
                return Violation.Direction.valueOf(directionStr);
            } catch (IllegalArgumentException e) {
                // 如果解析失败，使用默认值
            }
        }
        // 默认返回南向（可以根据实际情况调整）
        return Violation.Direction.SOUTH;
    }

    /**
     * 从检测结果中解析转弯类型信息
     */
    private Violation.TurnType parseTurnType(Map<String, Object> detectionResult, String violationType) {
        // 首先尝试从检测结果中获取
        if (detectionResult.containsKey("turnType")) {
            try {
                String turnTypeStr = detectionResult.get("turnType").toString().toUpperCase();
                return Violation.TurnType.valueOf(turnTypeStr);
            } catch (IllegalArgumentException e) {
                // 如果解析失败，继续使用推断逻辑
            }
        }

        // 根据违章类型推断转弯类型
        switch (violationType) {
            case "ILLEGAL_TURN":
                // 违法转弯可能是左转或右转，默认左转
                return Violation.TurnType.LEFT_TURN;
            case "RED_LIGHT":
                // 闯红灯可能是任何类型，默认直行
                return Violation.TurnType.STRAIGHT;
            case "WRONG_WAY":
                // 逆行通常是直行
                return Violation.TurnType.STRAIGHT;
            default:
                return Violation.TurnType.STRAIGHT;
        }
    }

    /**
     * 验证违章是否在红绿灯状态下成立
     * 
     * @param violationType  违章类型
     * @param intersectionId 路口ID
     * @param violationTime  违章时间
     * @return true-构成违章，false-不构成违章
     */
    private boolean validateViolationWithTrafficLight(String violationType, Long intersectionId,
            LocalDateTime violationTime) {
        switch (violationType) {
            case "RED_LIGHT":
                // 闯红灯：只有在红灯时才构成违章
                return trafficLightStateService.isRedLight(intersectionId, violationTime);

            case "ILLEGAL_TURN":
                // 违法转弯：在非转弯绿灯时间进行转弯
                // 只有转弯绿灯时转弯才合法，其他任何时间（转弯红灯、转弯黄灯、直行各种状态）都构成违章
                return !trafficLightStateService.isTurnGreenLight(intersectionId, violationTime);

            case "WRONG_WAY":
            case "CROSS_SOLID_LINE":
                // 逆行、跨实线：不受红绿灯状态影响，始终构成违章
                return true;

            default:
                // 其他类型默认不受红绿灯影响
                return true;
        }
    }

    /**
     * 违章类型映射
     * 将大模型识别的违章类型映射为系统标准类型
     */
    private String mapViolationType(String detectedType) {
        return switch (detectedType.toLowerCase()) {
            case "red_light", "闯红灯", "red light violation", "红灯违章" -> "RED_LIGHT";
            case "wrong_way", "逆行", "wrong direction", "逆向行驶" -> "WRONG_WAY";
            case "cross_solid_line", "跨实线", "solid line violation", "压实线" -> "CROSS_SOLID_LINE";
            case "illegal_turn", "违法转弯", "illegal turning", "违章转弯" -> "ILLEGAL_TURN";
            case "speeding", "超速", "speed violation" -> "SPEEDING";
            case "parking", "违章停车", "illegal parking" -> "PARKING_VIOLATION";
            default -> "OTHER";
        };
    }

    /**
     * 批量处理视频检测结果
     */
    public Map<String, Object> processBatchDetection(List<Map<String, Object>> detectionResults) {
        int totalCount = detectionResults.size();
        int successCount = 0;
        int errorCount = 0;
        int noViolationCount = 0;

        for (Map<String, Object> result : detectionResults) {
            try {
                processDetectionResult(result);
                successCount++;
            } catch (IllegalArgumentException e) {
                // 红绿灯状态下不构成违章
                noViolationCount++;
                System.out.println("不构成违章: " + e.getMessage());
            } catch (Exception e) {
                errorCount++;
                // 记录错误日志
                System.err.println("处理检测结果失败: " + e.getMessage());
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalCount", totalCount);
        summary.put("successCount", successCount);
        summary.put("errorCount", errorCount);
        summary.put("noViolationCount", noViolationCount);
        summary.put("successRate", totalCount > 0 ? (double) successCount / totalCount : 0.0);

        return summary;
    }
}