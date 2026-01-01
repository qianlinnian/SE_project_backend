package com.traffic.management.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器 - TrafficMind 交通智脑
 *
 * 提供图片文件上传和下载接口
 * 用于存储违规截图等文件
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Value("${file.upload.base-path:d:/traffic_uploads}")
    private String uploadBasePath;

    // 前端浏览器访问文件的URL（必须是外网地址，供浏览器访问）
    @Value("${file.url.base-url:http://localhost:8081}")
    private String fileUrlBaseUrl;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 上传文件
     *
     * POST /api/files/upload
     *
     * @param file 文件 (multipart/form-data)
     * @param type 文件类型 (可选: violation, vehicle, etc.)
     * @return 上传结果
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false, defaultValue = "general") String type) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 验证文件
            if (file.isEmpty()) {
                result.put("success", false);
                result.put("message", "文件为空");
                return ResponseEntity.badRequest().body(result);
            }

            // 2. 检查文件类型
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);

            if (!isAllowedExtension(extension)) {
                result.put("success", false);
                result.put("message", "不支持的文件类型: " + extension);
                return ResponseEntity.badRequest().body(result);
            }

            // 3. 生成唯一文件名
            String uniqueFilename = UUID.randomUUID().toString().replace("-", "") + "." + extension;

            // 4. 按日期组织目录结构
            String datePath = LocalDate.now().format(DATE_FORMATTER);
            String relativePath = type + "/" + datePath;
            Path targetDir = Paths.get(uploadBasePath, relativePath);

            // 创建目录
            Files.createDirectories(targetDir);

            // 5. 保存文件
            Path targetPath = targetDir.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 6. 构建访问URL（供前端浏览器访问）
            String fileUrl = fileUrlBaseUrl + "/download?filename=" + relativePath + "/" + uniqueFilename;

            result.put("success", true);
            result.put("filename", uniqueFilename);
            result.put("path", relativePath + "/" + uniqueFilename);
            result.put("url", fileUrl);
            result.put("originalName", originalFilename);
            result.put("size", file.getSize());
            result.put("type", type);

            return ResponseEntity.ok(result);

        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "文件保存失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 下载文件
     *
     * GET /api/files/download?filename=xxx
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(@RequestParam("filename") String filename) {
        try {
            // 防止路径遍历攻击（只移除..，保留正常的路径分隔符）
            String safeFilename = filename.replace("..", "");
            Path filePath = Paths.get(uploadBasePath, safeFilename);

            if (!filePath.startsWith(Paths.get(uploadBasePath))) {
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = Files.readAllBytes(filePath);
            String extension = getFileExtension(filename);

            // 根据扩展名设置Content-Type
            String contentType = getContentType(extension);

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", "inline; filename=\"" + safeFilename + "\"")
                    .body(fileContent);

        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 获取文件URL
     *
     * POST /api/files/get-url
     *
     * @param path 文件路径
     * @return 文件访问URL
     */
    @PostMapping("/get-url")
    public ResponseEntity<Map<String, Object>> getFileUrl(@RequestBody Map<String, String> request) {
        String path = request.get("path");

        if (path == null || path.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "缺少文件路径"
            ));
        }

        // 防止路径遍历攻击
        String safePath = path.replace("..", "");

        String url = fileUrlBaseUrl + "/download?filename=" + safePath;

        return ResponseEntity.ok(Map.of(
                "success", true,
                "path", safePath,
                "url", url
        ));
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 检查是否允许的文件类型
     */
    private boolean isAllowedExtension(String extension) {
        return extension.matches("^(jpg|jpeg|png|gif|bmp|webp)$");
    }

    /**
     * 根据扩展名获取Content-Type
     */
    private String getContentType(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
