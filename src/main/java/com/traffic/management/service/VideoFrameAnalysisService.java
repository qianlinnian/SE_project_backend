package com.traffic.management.service;

import com.traffic.management.entity.Violation;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 视频帧分析服务
 * 自动处理视频文件，提取帧并进行违章检测
 */
@Slf4j
@Service
public class VideoFrameAnalysisService {

    @Autowired
    private VideoDetectionService videoDetectionService;

    @Autowired
    private ViolationService violationService;

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${app.video.temp-dir:/tmp/traffic-videos}")
    private String tempVideoDir;

    @Value("${app.video.frame-interval:2}")
    private int frameInterval; // 每2秒提取一帧

    /**
     * 视频分析任务状态
     */
    public enum TaskStatus {
        PENDING, // 待处理
        PROCESSING, // 处理中
        COMPLETED, // 已完成
        FAILED // 失败
    }

    /**
     * 异步处理视频文件并分析帧
     * 
     * @param videoFile      视频文件
     * @param intersectionId 路口ID
     * @param cameraId       摄像头ID
     * @param taskId         任务ID
     * @return 处理结果
     */
    @Async
    public CompletableFuture<Map<String, Object>> processVideoAsync(
            MultipartFile videoFile, Long intersectionId, String cameraId, String taskId) {

        log.info("开始异步处理视频: taskId={}, file={}", taskId, videoFile.getOriginalFilename());

        try {
            // 1. 保存视频文件到临时目录
            Path tempDir = Paths.get(tempVideoDir);
            Files.createDirectories(tempDir);

            String fileName = taskId + "_" + videoFile.getOriginalFilename();
            Path videoPath = tempDir.resolve(fileName);
            videoFile.transferTo(videoPath.toFile());

            // 2. 上传原始视频到MinIO
            String videoUrl = uploadVideoToMinio(videoFile, intersectionId, taskId);

            // 3. 提取视频帧
            List<String> frameFiles = extractVideoFrames(videoPath.toString(), taskId);

            // 4. 对每个帧调用你的大模型检测API (模拟)
            List<Map<String, Object>> detectionResults = new ArrayList<>();
            for (String frameFile : frameFiles) {
                // 上传帧图片到MinIO
                String frameUrl = uploadFrameToMinio(frameFile, intersectionId, taskId);

                // 调用大模型检测 (这里模拟检测结果)
                Map<String, Object> detection = simulateFrameDetection(frameUrl, intersectionId, cameraId);
                if (detection != null) {
                    detectionResults.add(detection);
                }
            }

            // 5. 批量处理检测结果
            Map<String, Object> batchResult = videoDetectionService.processBatchDetection(detectionResults);

            // 6. 清理临时文件
            cleanupTempFiles(videoPath.toString(), frameFiles);

            // 7. 返回处理结果
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("status", TaskStatus.COMPLETED.name());
            result.put("videoUrl", videoUrl);
            result.put("totalFrames", frameFiles.size());
            result.put("detectionResults", batchResult);
            result.put("message", "视频分析完成");

            log.info("视频处理完成: taskId={}, 提取帧数={}, 检测违章数={}",
                    taskId, frameFiles.size(), batchResult.get("successCount"));

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("视频处理失败: taskId={}, error={}", taskId, e.getMessage(), e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("taskId", taskId);
            errorResult.put("status", TaskStatus.FAILED.name());
            errorResult.put("message", "视频处理失败: " + e.getMessage());

            return CompletableFuture.completedFuture(errorResult);
        }
    }

    /**
     * 提取视频帧
     */
    private List<String> extractVideoFrames(String videoPath, String taskId) throws Exception {
        List<String> frameFiles = new ArrayList<>();

        // 检查是否安装了FFmpeg
        try {
            FFmpeg ffmpeg = new FFmpeg("ffmpeg"); // 假设ffmpeg在PATH中

            // 创建帧输出目录
            String frameDir = tempVideoDir + File.separator + taskId + "_frames";
            Files.createDirectories(Paths.get(frameDir));

            // 构建FFmpeg命令：每frameInterval秒提取一帧
            FFmpegBuilder builder = new FFmpegBuilder()
                    .setInput(videoPath)
                    .overrideOutputFiles(true)
                    .addOutput(frameDir + File.separator + "frame_%04d.jpg")
                    .setVideoFrameRate(1.0 / frameInterval) // 每frameInterval秒一帧
                    .setFormat("image2")
                    .done();

            FFmpegExecutor executor = new FFmpegExecutor(ffmpeg);
            executor.createJob(builder).run();

            // 收集生成的帧文件
            File frameDirFile = new File(frameDir);
            if (frameDirFile.exists()) {
                File[] frames = frameDirFile.listFiles((dir, name) -> name.endsWith(".jpg"));
                if (frames != null) {
                    for (File frame : frames) {
                        frameFiles.add(frame.getAbsolutePath());
                    }
                }
            }

        } catch (Exception e) {
            log.warn("FFmpeg处理失败，使用Java备用方案: {}", e.getMessage());
            // 如果FFmpeg不可用，使用Java备用方案（简化版）
            frameFiles = extractFramesWithJava(videoPath, taskId);
        }

        return frameFiles;
    }

    /**
     * Java备用帧提取方案（简化版）
     */
    private List<String> extractFramesWithJava(String videoPath, String taskId) throws Exception {
        // 这是一个简化的实现，实际项目中可以集成更完整的视频处理库
        List<String> frameFiles = new ArrayList<>();

        // 创建帧目录
        String frameDir = tempVideoDir + File.separator + taskId + "_frames";
        Files.createDirectories(Paths.get(frameDir));

        // 模拟帧提取（实际应该使用专业的视频处理库）
        // 这里只创建一个占位符文件，实际使用时需要真正的视频处理
        String placeholderFrame = frameDir + File.separator + "frame_0001.jpg";
        Files.createFile(Paths.get(placeholderFrame));
        frameFiles.add(placeholderFrame);

        log.info("使用Java备用方案提取帧: {}", frameFiles.size());
        return frameFiles;
    }

    /**
     * 上传视频文件到MinIO
     */
    private String uploadVideoToMinio(MultipartFile videoFile, Long intersectionId, String taskId) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("intersection_%d/videos/%s_%s", intersectionId, taskId, timestamp);

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
     * 上传帧图片到MinIO
     */
    private String uploadFrameToMinio(String frameFilePath, Long intersectionId, String taskId) throws Exception {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
        String fileName = String.format("intersection_%d/frames/%s_%s.jpg", intersectionId, taskId, timestamp);

        File frameFile = new File(frameFilePath);
        try (FileInputStream fis = new FileInputStream(frameFile)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(fis, frameFile.length(), -1)
                            .contentType("image/jpeg")
                            .build());
        }

        return String.format("%s/%s/%s", minioEndpoint, bucketName, fileName);
    }

    /**
     * 模拟帧检测 - 在实际使用中，这里应该调用你的大模型API
     */
    private Map<String, Object> simulateFrameDetection(String frameUrl, Long intersectionId, String cameraId) {
        // 这里模拟检测结果，实际使用时应该调用你的大模型API
        // 模拟：30%概率检测到违章
        if (Math.random() > 0.7) {
            Map<String, Object> detection = new HashMap<>();
            detection.put("intersectionId", intersectionId);
            detection.put("plateNumber", "检测" + System.currentTimeMillis() % 10000);

            // 随机选择违章类型
            String[] violationTypes = { "闯红灯", "逆行", "跨实线", "违法转弯" };
            String violationType = violationTypes[(int) (Math.random() * violationTypes.length)];
            detection.put("violationType", violationType);

            detection.put("imageUrl", frameUrl);
            detection.put("confidence", 0.85 + Math.random() * 0.1); // 0.85-0.95
            detection.put("cameraId", cameraId);

            return detection;
        }

        return null; // 没有检测到违章
    }

    /**
     * 清理临时文件
     */
    private void cleanupTempFiles(String videoPath, List<String> frameFiles) {
        try {
            // 删除视频文件
            Files.deleteIfExists(Paths.get(videoPath));

            // 删除帧文件和目录
            for (String frameFile : frameFiles) {
                Files.deleteIfExists(Paths.get(frameFile));
            }

            // 删除帧目录
            if (!frameFiles.isEmpty()) {
                String frameDir = Paths.get(frameFiles.get(0)).getParent().toString();
                Files.deleteIfExists(Paths.get(frameDir));
            }

        } catch (Exception e) {
            log.warn("清理临时文件失败: {}", e.getMessage());
        }
    }

    /**
     * 在实际使用中，这个方法应该调用你的大模型API
     * 你可以替换这个方法来集成你的AI检测代码
     */
    public Map<String, Object> callYourAIModel(String frameImageUrl, Long intersectionId, String cameraId) {
        // TODO: 在这里调用你的大模型API
        // 示例实现：
        /*
         * try {
         * // 调用你的AI检测API
         * String aiApiUrl = "http://your-ai-service/detect";
         * Map<String, Object> requestData = Map.of(
         * "imageUrl", frameImageUrl,
         * "intersectionId", intersectionId,
         * "cameraId", cameraId
         * );
         * 
         * // 发送HTTP请求到你的AI服务
         * // ... HTTP请求代码 ...
         * 
         * // 解析AI返回的检测结果
         * // ... 解析响应 ...
         * 
         * return detectionResult;
         * } catch (Exception e) {
         * log.error("AI检测失败: {}", e.getMessage());
         * return null;
         * }
         */

        // 临时使用模拟数据
        return simulateFrameDetection(frameImageUrl, intersectionId, cameraId);
    }
}