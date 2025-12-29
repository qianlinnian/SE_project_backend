package com.traffic.management.controller;

import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 图片检测控制器 - TrafficMind 交通智脑
 *
 * 调用Python AI服务进行图片违规检测
 * 支持：闯红灯检测、压实线变道检测
 */
@RestController
@RequestMapping("/api/image-detection")
public class ImageDetectionController {

    private static final String AI_SERVICE_URL = System.getenv("AI_SERVICE_URL") != null
            ? System.getenv("AI_SERVICE_URL")
            : "http://ai-service:5000";
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 检测单张图片（multipart/form-data方式）
     *
     * POST /api/image-detection/detect
     *
     * 请求参数:
     * - image: 图片文件 (必填)
     * - signals: 信号灯状态JSON (可选)
     * - detect_types: 检测类型 (可选，默认检测两种)
     *
     * @return 检测结果
     */
    @PostMapping("/detect")
    public Map<String, Object> detectImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "signals", required = false) String signals,
            @RequestParam(value = "detect_types", required = false, defaultValue = "red_light,lane_change") String detectTypes) {

        try {
            // 1. 构建请求体
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", image.getResource());

            if (signals != null && !signals.isEmpty()) {
                body.add("signals", signals);
            }
            if (detectTypes != null && !detectTypes.isEmpty()) {
                body.add("detect_types", detectTypes);
            }

            // 2. 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 3. 调用AI服务
            String url = AI_SERVICE_URL + "/detect-image";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            // 4. 处理响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();

                // 如果检测到违规，自动上报到后端
                if (Boolean.TRUE.equals(result.get("success"))) {
                    Integer totalViolations = (Integer) result.get("total_violations");
                    if (totalViolations != null && totalViolations > 0) {
                        // 批量上报违规记录
                        autoReportViolations(result);
                    }
                }

                return result;
            } else {
                return Map.of(
                        "success", false,
                        "message", "AI服务调用失败: " + response.getStatusCode()
                );
            }

        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "message", "调用AI服务失败: " + e.getMessage()
            );
        }
    }

    /**
     * 检测单张图片（JSON方式，Base64编码）
     *
     * POST /api/image-detection/detect-base64
     *
     * 请求体:
     * {
     *   "image": "base64编码的图片数据",
     *   "signals": {"north_bound": "red", ...},  // 可选
     *   "detect_types": "red_light,lane_change"   // 可选
     * }
     *
     * @return 检测结果
     */
    @PostMapping("/detect-base64")
    public Map<String, Object> detectImageBase64(@RequestBody Map<String, Object> request) {
        try {
            // 1. 验证请求
            if (!request.containsKey("image")) {
                return Map.of(
                        "success", false,
                        "message", "缺少图片数据 (image)"
                );
            }

            // 2. 构建请求体
            Map<String, Object> body = new HashMap<>();
            body.put("image", request.get("image"));

            if (request.containsKey("signals")) {
                body.put("signals", request.get("signals"));
            }

            if (request.containsKey("detect_types")) {
                body.put("detect_types", request.get("detect_types"));
            }

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body);

            // 3. 调用AI服务
            String url = AI_SERVICE_URL + "/detect-image-base64";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            // 4. 处理响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();

                // 如果检测到违规，自动上报到后端
                if (Boolean.TRUE.equals(result.get("success"))) {
                    Integer totalViolations = (Integer) result.get("total_violations");
                    if (totalViolations != null && totalViolations > 0) {
                        autoReportViolations(result);
                    }
                }

                return result;
            } else {
                return Map.of(
                        "success", false,
                        "message", "AI服务调用失败: " + response.getStatusCode()
                );
            }

        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "message", "调用AI服务失败: " + e.getMessage()
            );
        }
    }

    /**
     * 批量检测多张图片
     *
     * POST /api/image-detection/detect-batch
     *
     * 请求参数:
     * - images: 多张图片文件
     * - signals: 信号灯状态JSON (可选)
     * - detect_types: 检测类型 (可选)
     *
     * @return 批量检测结果
     */
    @PostMapping("/detect-batch")
    public Map<String, Object> detectBatch(
            @RequestParam("images") MultipartFile[] images,
            @RequestParam(value = "signals", required = false) String signals,
            @RequestParam(value = "detect_types", required = false) String detectTypes) {

        try {
            // 1. 构建请求体
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            for (MultipartFile image : images) {
                body.add("images", image.getResource());
            }

            if (signals != null && !signals.isEmpty()) {
                body.add("signals", signals);
            }
            if (detectTypes != null && !detectTypes.isEmpty()) {
                body.add("detect_types", detectTypes);
            }

            // 2. 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 3. 调用AI服务
            String url = AI_SERVICE_URL + "/detect-batch";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            // 4. 处理响应
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();

                // 统计总违规数并上报
                if (Boolean.TRUE.equals(result.get("success"))) {
                    Integer totalViolations = (Integer) result.get("total_violations");
                    if (totalViolations != null && totalViolations > 0) {
                        result.put("auto_reported", true);
                    }
                }

                return result;
            } else {
                return Map.of(
                        "success", false,
                        "message", "AI服务调用失败: " + response.getStatusCode()
                );
            }

        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "message", "调用AI服务失败: " + e.getMessage()
            );
        }
    }

    /**
     * 闯红灯检测专用接口
     *
     * POST /api/image-detection/red-light
     *
     * @param image 图片文件
     * @param signals 信号灯状态（JSON格式）
     * @return 检测结果
     */
    @PostMapping("/red-light")
    public Map<String, Object> detectRedLight(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "signals", required = false, defaultValue = "{\"north_bound\":\"red\",\"south_bound\":\"red\",\"west_bound\":\"red\",\"east_bound\":\"red\"}") String signals) {

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", image.getResource());
            body.add("signals", signals);
            body.add("detect_types", "red_light");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String url = AI_SERVICE_URL + "/detect-image";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                if (Boolean.TRUE.equals(result.get("success"))) {
                    Integer totalViolations = (Integer) result.get("total_violations");
                    if (totalViolations != null && totalViolations > 0) {
                        autoReportViolations(result);
                    }
                }
                return result;
            }

            return Map.of("success", false, "message", "检测失败");

        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 压实线变道检测专用接口
     *
     * POST /api/image-detection/lane-change
     *
     * @param image 图片文件
     * @return 检测结果
     */
    @PostMapping("/lane-change")
    public Map<String, Object> detectLaneChange(@RequestParam("image") MultipartFile image) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image", image.getResource());
            body.add("detect_types", "lane_change");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String url = AI_SERVICE_URL + "/detect-image";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = response.getBody();
                if (Boolean.TRUE.equals(result.get("success"))) {
                    Integer totalViolations = (Integer) result.get("total_violations");
                    if (totalViolations != null && totalViolations > 0) {
                        autoReportViolations(result);
                    }
                }
                return result;
            }

            return Map.of("success", false, "message", "检测失败");

        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    /**
     * 获取AI服务健康状态
     *
     * GET /api/image-detection/health
     *
     * @return AI服务状态
     */
    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    AI_SERVICE_URL + "/health",
                    HttpMethod.GET,
                    null,
                    new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> health = response.getBody();
                health.put("backend_status", "connected");
                return health;
            }

            return Map.of(
                    "status", "unhealthy",
                    "ai_service_status", "unreachable",
                    "message", "无法连接到AI服务"
            );

        } catch (Exception e) {
            return Map.of(
                    "status", "unhealthy",
                    "ai_service_status", "error",
                    "message", "连接AI服务失败: " + e.getMessage()
            );
        }
    }

    /**
     * 自动上报检测到的违规记录到后端
     *
     * @param detectionResult AI检测结果
     */
    @SuppressWarnings("unchecked")
    private void autoReportViolations(Map<String, Object> detectionResult) {
        try {
            List<Map<String, Object>> violations = (List<Map<String, Object>>) detectionResult.get("violations");
            if (violations == null || violations.isEmpty()) {
                return;
            }

            // 调用后端违规上报接口
            String baseUrl = System.getenv("SERVER_BASE_URL") != null
                    ? System.getenv("SERVER_BASE_URL")
                    : "http://backend:8081";
            String reportUrl = baseUrl + "/api/violations/report";

            for (Map<String, Object> violation : violations) {
                try {
                    Map<String, Object> reportData = new HashMap<>();
                    reportData.put("intersectionId", 1);
                    reportData.put("direction", mapDirection(violation.getOrDefault("direction", "north_bound").toString()));
                    reportData.put("turnType", "STRAIGHT");
                    reportData.put("plateNumber", "AI_" + violation.getOrDefault("vehicle_index", 0));
                    reportData.put("violationType", mapViolationType(violation.getOrDefault("type", "").toString()));
                    reportData.put("imageUrl", violation.getOrDefault("screenshot", ""));
                    reportData.put("aiConfidence", violation.getOrDefault("confidence", 0.95));
                    reportData.put("occurredAt", violation.getOrDefault("timestamp", java.time.LocalDateTime.now().toString()));

                    HttpEntity<Map<String, Object>> request = new HttpEntity<>(reportData);
                    restTemplate.exchange(
                            reportUrl,
                            HttpMethod.POST,
                            request,
                            new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {}
                    );

                } catch (Exception e) {
                    // 单个违规上报失败不影响其他违规
                }
            }

        } catch (Exception e) {
            // 上报失败不影响主流程
        }
    }

    /**
     * 映射内部方向格式到后端格式
     */
    private String mapDirection(String direction) {
        Map<String, String> mapping = Map.of(
                "north_bound", "NORTH",
                "south_bound", "SOUTH",
                "west_bound", "WEST",
                "east_bound", "EAST"
        );
        return mapping.getOrDefault(direction.toLowerCase(), "NORTH");
    }

    /**
     * 映射内部违规类型到后端格式
     */
    private String mapViolationType(String violationType) {
        Map<String, String> mapping = Map.of(
                "red_light_running", "RED_LIGHT",
                "lane_change_across_solid_line", "CROSS_SOLID_LINE"
        );
        return mapping.getOrDefault(violationType, "OTHER");
    }
}
