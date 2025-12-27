package com.traffic.management.service;

import com.traffic.management.dto.request.SignalAdjustRequest;
import com.traffic.management.dto.response.SignalConfigResponse;
import com.traffic.management.entity.SignalConfig;
import com.traffic.management.entity.SignalLog;
import com.traffic.management.exception.BusinessException;
import com.traffic.management.exception.ErrorCode;
import com.traffic.management.repository.SignalConfigRepository;
import com.traffic.management.repository.SignalLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 信号灯服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignalService {

    private final SignalConfigRepository signalConfigRepository;
    private final SignalLogRepository signalLogRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SIGNAL_CACHE_PREFIX = "signal:config:";
    private static final long SIGNAL_CACHE_TTL = 5; // 5分钟

    /**
     * 调整信号灯配置
     */
    @Transactional
    public SignalConfigResponse adjustSignal(Long intersectionId, SignalAdjustRequest request, Long operatorId) {
        // 获取或创建信号灯配置
        SignalConfig config = signalConfigRepository.findByIntersectionId(intersectionId)
                .orElse(SignalConfig.builder()
                        .intersectionId(intersectionId)
                        .signalMode(SignalConfig.SignalMode.FIXED) // 默认模式
                        .greenDuration(30) // 默认绿灯时长
                        .redDuration(30)
                        .yellowDuration(3)
                        .currentPhase(SignalConfig.SignalPhase.GREEN)
                        .phaseRemaining(0)
                        .cycleTime(63) // 默认周期
                        .build());

        // 保存旧配置用于日志
        Map<String, Object> oldConfig = buildConfigMap(config);

        // 更新配置
        try {
            config.setSignalMode(SignalConfig.SignalMode.valueOf(request.getMode().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_SIGNAL_MODE);
        }

        config.setGreenDuration(request.getGreenDuration());
        config.setLastAdjustedAt(LocalDateTime.now());
        config.setLastAdjustedBy(operatorId);

        config = signalConfigRepository.save(config);

        // 更新Redis缓存
        String cacheKey = SIGNAL_CACHE_PREFIX + intersectionId;
        redisTemplate.opsForValue().set(cacheKey, config, SIGNAL_CACHE_TTL, TimeUnit.MINUTES);

        // 异步记录日志
        saveSignalLog(intersectionId, oldConfig, buildConfigMap(config), operatorId, request.getReason());

        log.info("调整路口 {} 信号灯配置成功", intersectionId);

        return buildResponse(config);
    }

    /**
     * 获取信号灯配置
     */
    public SignalConfigResponse getSignalConfig(Long intersectionId) {
        // 先从Redis缓存获取
        String cacheKey = SIGNAL_CACHE_PREFIX + intersectionId;
        SignalConfig config = (SignalConfig) redisTemplate.opsForValue().get(cacheKey);

        if (config == null) {
            // 缓存未命中，从数据库查询
            config = signalConfigRepository.findByIntersectionId(intersectionId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SIGNAL_CONFIG_NOT_FOUND));

            // 写入缓存
            redisTemplate.opsForValue().set(cacheKey, config, SIGNAL_CACHE_TTL, TimeUnit.MINUTES);
        }

        return buildResponse(config);
    }

    /**
     * 获取所有路口信号灯配置
     */
    public List<SignalConfigResponse> getAllSignalConfigs() {
        List<SignalConfig> configs = signalConfigRepository.findAll();

        return configs.stream()
                .map(this::buildResponse)
                .collect(Collectors.toList());
    }

    /**
     * 构建响应对象
     */
    private SignalConfigResponse buildResponse(SignalConfig config) {
        return SignalConfigResponse.builder()
                .intersectionId(config.getIntersectionId())
                .intersectionName(null)
                .mode(config.getSignalMode().name())
                .currentPhase(config.getCurrentPhase().name())
                .greenDuration(config.getGreenDuration())
                .redDuration(config.getRedDuration())
                .yellowDuration(config.getYellowDuration())
                .straightRedDuration(config.getStraightRedDuration())
                .straightYellowDuration(config.getStraightYellowDuration())
                .straightGreenDuration(config.getStraightGreenDuration())
                .turnRedDuration(config.getTurnRedDuration())
                .turnYellowDuration(config.getTurnYellowDuration())
                .turnGreenDuration(config.getTurnGreenDuration())
                .phaseRemaining(config.getPhaseRemaining())
                .cycleTime(config.getCycleTime())
                .lastAdjustedAt(config.getLastAdjustedAt())
                .build();
    }

    /**
     * 构建配置Map
     */
    private Map<String, Object> buildConfigMap(SignalConfig config) {
        Map<String, Object> map = new HashMap<>();
        map.put("mode", config.getSignalMode() != null ? config.getSignalMode().name() : "UNKNOWN");
        map.put("greenDuration", config.getGreenDuration());
        map.put("redDuration", config.getRedDuration());
        map.put("yellowDuration", config.getYellowDuration());
        map.put("cycleTime", config.getCycleTime());
        return map;
    }

    /**
     * 保存信号灯日志
     */
    private void saveSignalLog(Long intersectionId, Map<String, Object> oldConfig,
            Map<String, Object> newConfig, Long operatorId, String reason) {
        SignalLog log = SignalLog.builder()
                .intersectionId(intersectionId)
                .actionType(
                        operatorId != null ? SignalLog.ActionType.MANUAL_OVERRIDE : SignalLog.ActionType.AUTO_ADJUST)
                .oldConfig(oldConfig.isEmpty() ? null : oldConfig)
                .newConfig(newConfig)
                .operatorId(operatorId)
                .reason(reason)
                .build();

        signalLogRepository.save(log);
    }
}
