package com.traffic.management.service;

import com.traffic.management.entity.Violation;
import com.traffic.management.repository.ViolationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ViolationService {

    @Autowired
    private ViolationRepository violationRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String VIOLATION_CACHE_PREFIX = "violation:";
    private static final String VIOLATIONS_LIST_KEY = "violations:list";
    private static final String VIOLATION_COUNT_KEY = "violation:total:today";
    private static final long CACHE_DURATION_MINUTES = 30;

    /**
     * 上报违章：同时写入 MySQL（持久化）和 Redis（实时查询）
     */
    @Transactional
    public Violation reportViolation(Map<String, Object> violationData) {
        // 1. 构建 Violation 对象
        Long intersectionId;
        Object idValue = violationData.get("intersectionId");

        // 支持数字字符串 "1" 或直接数字 1，或者 "intersection-1" 这种格式
        if (idValue instanceof Number) {
            intersectionId = ((Number) idValue).longValue();
        } else {
            String idStr = idValue.toString();
            // 如果是 "intersection-1" 这种格式，提取数字部分
            if (idStr.contains("-")) {
                idStr = idStr.substring(idStr.lastIndexOf("-") + 1);
            }
            try {
                intersectionId = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                intersectionId = 1L; // 默认值
            }
        }

        // 2. 获取违章类型：优先使用 violationType，如果没有则使用 type
        String violationTypeStr = violationData.containsKey("violationType")
                ? violationData.get("violationType").toString()
                : (violationData.containsKey("type") ? violationData.get("type").toString() : "RED_LIGHT");

        // 转换为枚举，支持多种格式
        Violation.ViolationType violationType;
        try {
            violationType = Violation.ViolationType.valueOf(violationTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 如果不是标准的违章类型，转换为基本类型
            violationType = switch (violationTypeStr.toLowerCase()) {
                case "speeding", "超速" -> Violation.ViolationType.SPEEDING;
                case "red_light", "red light", "闯红灯" -> Violation.ViolationType.RED_LIGHT;
                case "wrong_way", "逆行", "wrong direction" -> Violation.ViolationType.WRONG_WAY;
                case "cross_solid_line", "跨实线", "压实线" -> Violation.ViolationType.CROSS_SOLID_LINE;
                case "illegal_turn", "违法转弯", "违章转弯" -> Violation.ViolationType.ILLEGAL_TURN;
                case "parking", "违章停车" -> Violation.ViolationType.PARKING_VIOLATION;
                default -> Violation.ViolationType.OTHER;
            };
        }

        // 3. 获取车牌号：优先使用 plateNumber，如果没有则使用 vehicleId
        String plateNumber = violationData.containsKey("plateNumber") ? violationData.get("plateNumber").toString()
                : (violationData.containsKey("vehicleId") ? violationData.get("vehicleId").toString() : "UNKNOWN");

        // 4. 获取图片URL：优先使用 imageUrl，如果没有则使用 description 或默认值
        String imageUrl = violationData.containsKey("imageUrl") ? violationData.get("imageUrl").toString()
                : (violationData.containsKey("description") ? violationData.get("description").toString()
                        : "https://example.com/default.jpg");

        // 5. 获取 AI 置信度
        Float aiConfidence = 0.95f;
        if (violationData.containsKey("aiConfidence")) {
            try {
                aiConfidence = Float.parseFloat(violationData.get("aiConfidence").toString());
            } catch (NumberFormatException e) {
                aiConfidence = 0.95f;
            }
        }

        // 6. 获取方向信息
        Violation.Direction direction = null;
        if (violationData.containsKey("direction")) {
            try {
                direction = Violation.Direction.valueOf(violationData.get("direction").toString().toUpperCase());
            } catch (IllegalArgumentException e) {
                // 忽略无效的方向值
            }
        }

        // 7. 获取转弯类型
        Violation.TurnType turnType = null;
        if (violationData.containsKey("turnType")) {
            try {
                turnType = Violation.TurnType.valueOf(violationData.get("turnType").toString().toUpperCase());
            } catch (IllegalArgumentException e) {
                // 忽略无效的转弯类型
            }
        }

        Violation violation = Violation.builder()
                .intersectionId(intersectionId)
                .direction(direction)
                .turnType(turnType)
                .plateNumber(plateNumber)
                .violationType(violationType)
                .imageUrl(imageUrl)
                .aiConfidence(aiConfidence)
                .occurredAt(LocalDateTime.now())
                .status(Violation.ViolationStatus.PENDING)
                .appealStatus(Violation.AppealStatus.NO_APPEAL)
                .build();

        // 6. 写入 MySQL（持久化保存）
        Violation savedViolation = violationRepository.save(violation);

        // 7. 同时写入 Redis（用于实时查询和缓存）
        String cacheKey = VIOLATION_CACHE_PREFIX + savedViolation.getId();
        redisTemplate.opsForHash().putAll(cacheKey, convertToMap(savedViolation));
        redisTemplate.expire(cacheKey, Duration.ofMinutes(CACHE_DURATION_MINUTES));

        // 8. 增加违章计数（实时统计）
        redisTemplate.opsForValue().increment(VIOLATION_COUNT_KEY);

        // 9. 添加到违章列表（用于快速列表查询）
        redisTemplate.opsForList().leftPush(VIOLATIONS_LIST_KEY, convertToMap(savedViolation));
        redisTemplate.expire(VIOLATIONS_LIST_KEY, Duration.ofDays(7));

        return savedViolation;
    }

    /**
     * 查询违章列表：支持搜索和类型筛选
     */
    public List<Map<String, Object>> getViolations(int page, int size, String search, String type) {
        // 如果有搜索或类型筛选条件，直接查询数据库（不使用Redis缓存）
        if ((search != null && !search.isEmpty()) || (type != null && !type.isEmpty())) {
            return getViolationsWithFilter(page, size, search, type);
        }

        // 无筛选条件时，直接从数据库查询（Redis List缓存不支持分页，已移除）
        // 注意:前端已经将page转换为0-based(page-1),所以这里直接使用page
        Pageable pageable = PageRequest.of(page, size);
        Page<Violation> violationPage = violationRepository.findAll(pageable);

        // 转换为Map并缓存单条记录
        List<Map<String, Object>> result = new ArrayList<>();
        for (Violation v : violationPage.getContent()) {
            Map<String, Object> map = convertToMap(v);
            result.add(map);
            // 缓存单条记录（用于详情查询加速）
            String cacheKey = VIOLATION_CACHE_PREFIX + v.getId();
            redisTemplate.opsForHash().putAll(cacheKey, map);
            redisTemplate.expire(cacheKey, Duration.ofMinutes(CACHE_DURATION_MINUTES));
        }

        return result;
    }

    /**
     * 带筛选条件的查询
     */
    private List<Map<String, Object>> getViolationsWithFilter(int page, int size, String search, String type) {
        // 注意:前端已经将page转换为0-based(page-1),所以这里直接使用page
        Pageable pageable = PageRequest.of(page, size);
        List<Violation> violations;

        if (type != null && !type.isEmpty()) {
            // 按类型筛选
            try {
                Violation.ViolationType violationType = Violation.ViolationType.valueOf(type);
                if (search != null && !search.isEmpty()) {
                    // 同时有搜索和类型筛选
                    violations = violationRepository.findByViolationTypeAndPlateNumberContaining(
                            violationType, search, pageable).getContent();
                } else {
                    // 只有类型筛选
                    violations = violationRepository.findByViolationType(violationType, pageable).getContent();
                }
            } catch (IllegalArgumentException e) {
                // 类型无效，返回空列表
                return new ArrayList<>();
            }
        } else if (search != null && !search.isEmpty()) {
            // 只有搜索条件（按车牌号搜索）
            violations = violationRepository.findByPlateNumberContaining(search, pageable).getContent();
        } else {
            // 无筛选条件
            violations = violationRepository.findAll(pageable).getContent();
        }

        // 转换为Map
        List<Map<String, Object>> result = new ArrayList<>();
        for (Violation v : violations) {
            result.add(convertToMap(v));
        }
        return result;
    }

    /**
     * 获取单条违章详情：优先从 Redis，然后从 MySQL
     */
    public Map<Object, Object> getViolationDetail(String violationId) {
        String cacheKey = VIOLATION_CACHE_PREFIX + violationId;

        // 1. 先从 Redis 查询
        Map<Object, Object> cached = redisTemplate.opsForHash().entries(cacheKey);
        if (!cached.isEmpty()) {
            return cached;
        }

        // 2. Redis 缓存未命中，从 MySQL 查询
        try {
            Long id = Long.parseLong(violationId);
            Optional<Violation> violation = violationRepository.findById(id);
            if (violation.isPresent()) {
                // 3. 将查询结果写入 Redis 缓存
                Map<String, Object> violationMap = convertToMap(violation.get());
                redisTemplate.opsForHash().putAll(cacheKey, violationMap);
                redisTemplate.expire(cacheKey, Duration.ofMinutes(CACHE_DURATION_MINUTES));
                return new HashMap<>(violationMap);
            }
        } catch (NumberFormatException e) {
            // ID 格式不对，返回空结果
        }

        return new HashMap<>();
    }

    /**
     * 处理违章：更新 MySQL 记录和 Redis 缓存
     */
    @Transactional
    public void processViolation(String violationId, Map<String, Object> processInfo) {
        try {
            Long id = Long.parseLong(violationId);
            Optional<Violation> violationOpt = violationRepository.findById(id);

            if (violationOpt.isPresent()) {
                Violation violation = violationOpt.get();

                // 1. 更新 Violation 对象
                violation.setStatus(Violation.ViolationStatus.CONFIRMED);
                violation.setProcessedAt(LocalDateTime.now());
                if (processInfo.containsKey("processedBy")) {
                    violation.setProcessedBy(Long.parseLong(processInfo.get("processedBy").toString()));
                }
                if (processInfo.containsKey("penaltyAmount")) {
                    violation.setPenaltyAmount(new java.math.BigDecimal(processInfo.get("penaltyAmount").toString()));
                }
                if (processInfo.containsKey("reviewNotes")) {
                    violation.setReviewNotes(processInfo.get("reviewNotes").toString());
                }

                // 2. 写入 MySQL（更新持久化数据）
                Violation updated = violationRepository.save(violation);

                // 3. 改进的缓存更新策略：先删除旧缓存，再写入新数据（保证一致性）
                String cacheKey = VIOLATION_CACHE_PREFIX + id;

                // 先删除旧缓存（避免脏数据）
                redisTemplate.delete(cacheKey);

                // 写入最新数据到缓存
                redisTemplate.opsForHash().putAll(cacheKey, convertToMap(updated));
                redisTemplate.expire(cacheKey, Duration.ofMinutes(CACHE_DURATION_MINUTES));

                // 4. 清除列表缓存（因为列表中的数据状态已改变）
                redisTemplate.delete(VIOLATIONS_LIST_KEY);
            }
        } catch (NumberFormatException e) {
            // ID 格式不对，忽略
        }
    }

    /**
     * 获取违章总数：从 Redis 实时读取
     */
    public Long getViolationCount() {
        Object value = redisTemplate.opsForValue().get(VIOLATION_COUNT_KEY);
        return value != null ? Long.parseLong(value.toString()) : 0L;
    }

    /**
     * 获取带筛选条件的违章总数：直接查询数据库
     */
    public Long getViolationCountWithFilter(String search, String type) {
        // 如果没有筛选条件，直接查询所有记录数
        if ((search == null || search.isEmpty()) && (type == null || type.isEmpty())) {
            return violationRepository.count();
        }

        // 如果有筛选条件，需要根据条件统计
        try {
            if (search != null && !search.isEmpty() && type != null && !type.isEmpty()) {
                // 同时有车牌和类型筛选
                Violation.ViolationType violationType = Violation.ViolationType.valueOf(type);
                return (long) violationRepository.findByViolationTypeAndPlateNumberContaining(
                        violationType, search, org.springframework.data.domain.Pageable.unpaged()
                ).getTotalElements();
            } else if (search != null && !search.isEmpty()) {
                // 仅车牌筛选
                return (long) violationRepository.findByPlateNumberContaining(
                        search, org.springframework.data.domain.Pageable.unpaged()
                ).getTotalElements();
            } else {
                // 仅类型筛选
                Violation.ViolationType violationType = Violation.ViolationType.valueOf(type);
                return (long) violationRepository.findByViolationType(
                        violationType, org.springframework.data.domain.Pageable.unpaged()
                ).getTotalElements();
            }
        } catch (IllegalArgumentException e) {
            // 如果类型无效，返回所有记录数
            return violationRepository.count();
        }
    }

    /**
     * 增加违章计数（每次调用 +1）
     */
    public Long incrementViolationCount() {
        return redisTemplate.opsForValue().increment(VIOLATION_COUNT_KEY);
    }

    /**
     * 重置违章计数（每天凌晨调用）
     */
    public void resetViolationCount() {
        redisTemplate.delete(VIOLATION_COUNT_KEY);
    }

    /**
     * 将 Violation Entity 转换为 Map（用于 Redis 存储）
     */
    private Map<String, Object> convertToMap(Violation violation) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", violation.getId());
        map.put("intersectionId", violation.getIntersectionId());
        map.put("direction", violation.getDirection() != null ? violation.getDirection().toString() : null);
        map.put("turnType", violation.getTurnType() != null ? violation.getTurnType().toString() : null);
        map.put("plateNumber", violation.getPlateNumber());
        map.put("vehicleType", violation.getVehicleType());
        map.put("violationType", violation.getViolationType() != null ? violation.getViolationType().toString() : null);
        map.put("imageUrl", violation.getImageUrl());
        map.put("aiConfidence", violation.getAiConfidence());
        map.put("occurredAt", violation.getOccurredAt() != null ? violation.getOccurredAt().toString() : null);
        map.put("status", violation.getStatus() != null ? violation.getStatus().toString() : null);
        map.put("processedBy", violation.getProcessedBy());
        map.put("processedAt", violation.getProcessedAt() != null ? violation.getProcessedAt().toString() : null);
        map.put("penaltyAmount", violation.getPenaltyAmount());
        map.put("reviewNotes", violation.getReviewNotes());
        map.put("appealStatus", violation.getAppealStatus() != null ? violation.getAppealStatus().toString() : null);
        map.put("createdAt", violation.getCreatedAt() != null ? violation.getCreatedAt().toString() : null);
        map.put("updatedAt", violation.getUpdatedAt() != null ? violation.getUpdatedAt().toString() : null);
        return map;
    }

    // ========== 统计分析方法 ==========

    /**
     * 获取统计概览（支持时间范围）
     */
    public Map<String, Object> getStatisticsOverview(LocalDateTime startTime, LocalDateTime endTime) {
        // 计算当前时间段的统计
        long total = violationRepository.countByOccurredAtBetween(startTime, endTime);
        long pending = violationRepository.countByStatusAndOccurredAtBetween(
                Violation.ViolationStatus.PENDING, startTime, endTime);
        long confirmed = violationRepository.countByStatusAndOccurredAtBetween(
                Violation.ViolationStatus.CONFIRMED, startTime, endTime);
        long rejected = violationRepository.countByStatusAndOccurredAtBetween(
                Violation.ViolationStatus.REJECTED, startTime, endTime);

        // 计算环比增长率（与前一个相同时长的时间段对比）
        Duration duration = Duration.between(startTime, endTime);
        LocalDateTime prevStartTime = startTime.minus(duration);
        LocalDateTime prevEndTime = startTime;
        long prevTotal = violationRepository.countByOccurredAtBetween(prevStartTime, prevEndTime);

        double growthRate = 0.0;
        if (prevTotal > 0) {
            growthRate = ((double) (total - prevTotal) / prevTotal) * 100;
            growthRate = Math.round(growthRate * 100) / 100.0; // 保留两位小数
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("pending", pending);
        result.put("confirmed", confirmed);
        result.put("rejected", rejected);
        result.put("growthRate", growthRate);
        return result;
    }

    /**
     * 按违规类型统计
     */
    public Map<String, Object> getStatisticsByType(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> results = violationRepository.countByViolationTypeGrouped(startTime, endTime);

        List<Map<String, Object>> data = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("type", row[0].toString());
            item.put("typeName", getViolationTypeName(row[0].toString()));
            item.put("count", ((Number) row[1]).longValue());
            data.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        return result;
    }

    /**
     * 获取时间趋势数据
     */
    public Map<String, Object> getStatisticsTrend(String granularity, LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> results;

        if ("hour".equalsIgnoreCase(granularity)) {
            results = violationRepository.countByHourGrouped(startTime, endTime);
        } else {
            results = violationRepository.countByDayGrouped(startTime, endTime);
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", row[0].toString());
            item.put("count", ((Number) row[1]).longValue());
            data.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("granularity", granularity);
        result.put("data", data);
        return result;
    }

    /**
     * 获取热力图数据（小时 × 星期）
     */
    public Map<String, Object> getStatisticsHeatmap(LocalDateTime startTime, LocalDateTime endTime) {
        List<Object[]> results = violationRepository.getHeatmapData(startTime, endTime);

        List<List<Object>> data = new ArrayList<>();
        for (Object[] row : results) {
            List<Object> item = new ArrayList<>();
            int hour = ((Number) row[0]).intValue();
            int dayOfWeek = ((Number) row[1]).intValue() - 1; // MySQL DAYOFWEEK 1-7，转为 0-6
            long count = ((Number) row[2]).longValue();

            item.add(hour);      // x: 小时
            item.add(dayOfWeek); // y: 星期
            item.add(count);     // value: 数量
            data.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        return result;
    }

    /**
     * 获取TOP违规车牌
     */
    public Map<String, Object> getTopViolators(int limit, LocalDateTime startTime, LocalDateTime endTime) {
        org.springframework.data.domain.PageRequest pageRequest =
            org.springframework.data.domain.PageRequest.of(0, limit);
        List<Object[]> results = violationRepository.findTopViolators(startTime, endTime, pageRequest);

        // 按车牌号聚合
        Map<String, Map<String, Object>> violatorMap = new HashMap<>();
        for (Object[] row : results) {
            String plateNumber = row[0].toString();
            long count = ((Number) row[1]).longValue();
            String type = row[2].toString();

            if (!violatorMap.containsKey(plateNumber)) {
                Map<String, Object> violator = new HashMap<>();
                violator.put("plateNumber", plateNumber);
                violator.put("count", 0L);
                violator.put("typeBreakdown", new HashMap<String, Long>());
                violatorMap.put(plateNumber, violator);
            }

            Map<String, Object> violator = violatorMap.get(plateNumber);
            violator.put("count", ((Number) violator.get("count")).longValue() + count);

            @SuppressWarnings("unchecked")
            Map<String, Long> typeBreakdown = (Map<String, Long>) violator.get("typeBreakdown");
            typeBreakdown.put(type, count);
        }

        // 转换为列表并排序
        List<Map<String, Object>> data = new ArrayList<>(violatorMap.values());
        data.sort((a, b) -> Long.compare(
            ((Number) b.get("count")).longValue(),
            ((Number) a.get("count")).longValue()
        ));

        // 只保留前N个
        if (data.size() > limit) {
            data = data.subList(0, limit);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        return result;
    }

    /**
     * 辅助方法：获取违规类型中文名
     */
    private String getViolationTypeName(String type) {
        return switch (type) {
            case "RED_LIGHT" -> "闯红灯";
            case "WRONG_WAY" -> "逆行";
            case "CROSS_SOLID_LINE" -> "跨实线";
            case "ILLEGAL_TURN" -> "违法转弯";
            case "WAITING_AREA_RED_ENTRY" -> "待转区红灯进入";
            case "WAITING_AREA_ILLEGAL_EXIT" -> "待转区非法驶离";
            case "SPEEDING" -> "超速";
            case "PARKING_VIOLATION" -> "违章停车";
            default -> "其他";
        };
    }
}
