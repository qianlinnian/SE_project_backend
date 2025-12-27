package com.traffic.management.service;

import com.traffic.management.entity.SignalConfig;
import com.traffic.management.repository.SignalConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 红绿灯状态检测服务
 * 用于判断违章检测时的红绿灯状态
 */
@Service
public class TrafficLightStateService {

    @Autowired
    private SignalConfigRepository signalConfigRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String SIGNAL_STATE_CACHE_PREFIX = "signal:state:";
    private static final long CACHE_DURATION_SECONDS = 10; // 缓存10秒

    /**
     * 红绿灯状态枚举
     */
    public enum LightState {
        RED, // 红灯（保持向后兼容）
        YELLOW, // 黄灯（保持向后兼容）
        GREEN, // 绿灯（保持向后兼容）
        STRAIGHT_RED, // 直行红灯
        STRAIGHT_YELLOW, // 直行黄灯
        STRAIGHT_GREEN, // 直行绿灯
        TURN_RED, // 转弯红灯
        TURN_YELLOW, // 转弯黄灯
        TURN_GREEN // 转弯绿灯
    }

    /**
     * 获取指定路口当前的红绿灯状态
     * 
     * @param intersectionId 路口ID
     * @param checkTime      检查时间（违章发生时间）
     * @return 红绿灯状态
     */
    public LightState getCurrentLightState(Long intersectionId, LocalDateTime checkTime) {
        String cacheKey = SIGNAL_STATE_CACHE_PREFIX + intersectionId;

        // 先从缓存获取
        Object cachedState = redisTemplate.opsForValue().get(cacheKey);
        if (cachedState != null) {
            return LightState.valueOf(cachedState.toString());
        }

        // 从数据库获取信号灯配置
        SignalConfig config = signalConfigRepository.findByIntersectionId(intersectionId)
                .orElse(null);

        if (config == null) {
            // 如果没有配置，默认返回红灯（保守策略）
            return LightState.RED;
        }

        // 计算当前应该是什么灯
        LightState currentState = calculateLightState(config, checkTime);

        // 缓存状态
        redisTemplate.opsForValue().set(cacheKey, currentState.name(),
                CACHE_DURATION_SECONDS, TimeUnit.SECONDS);

        return currentState;
    }

    /**
     * 根据信号灯配置和时间计算当前灯色状态
     * 支持独立的直行和转弯信号周期
     */
    private LightState calculateLightState(SignalConfig config, LocalDateTime checkTime) {
        // 计算从当天0点开始的秒数
        int secondsOfDay = checkTime.getHour() * 3600 +
                checkTime.getMinute() * 60 +
                checkTime.getSecond();

        // 获取直行信号灯配置
        int straightRed = config.getStraightRedDuration() != null ? config.getStraightRedDuration()
                : config.getRedDuration();
        int straightYellow = config.getStraightYellowDuration() != null ? config.getStraightYellowDuration()
                : config.getYellowDuration();
        int straightGreen = config.getStraightGreenDuration() != null ? config.getStraightGreenDuration()
                : (config.getGreenDuration() != null ? (int) (config.getGreenDuration() * 0.6) : 30);

        // 获取转弯信号灯配置
        int turnRed = config.getTurnRedDuration() != null ? config.getTurnRedDuration()
                : (straightRed + straightGreen + straightYellow);
        int turnYellow = config.getTurnYellowDuration() != null ? config.getTurnYellowDuration() : 3;
        int turnGreen = config.getTurnGreenDuration() != null ? config.getTurnGreenDuration()
                : (config.getGreenDuration() != null ? (config.getGreenDuration() - straightGreen) : 15);

        // 计算直行周期
        int straightCycle = straightRed + straightYellow + straightGreen;
        int straightPosition = secondsOfDay % straightCycle;

        // 计算转弯周期
        int turnCycle = turnRed + turnYellow + turnGreen;
        int turnPosition = secondsOfDay % turnCycle;

        // 确定直行信号状态
        LightState straightState;
        if (straightPosition < straightRed) {
            straightState = LightState.STRAIGHT_RED;
        } else if (straightPosition < straightRed + straightGreen) {
            straightState = LightState.STRAIGHT_GREEN;
        } else {
            straightState = LightState.STRAIGHT_YELLOW;
        }

        // 确定转弯信号状态
        LightState turnState;
        if (turnPosition < turnRed) {
            turnState = LightState.TURN_RED;
        } else if (turnPosition < turnRed + turnGreen) {
            turnState = LightState.TURN_GREEN;
        } else {
            turnState = LightState.TURN_YELLOW;
        }

        // 优先返回绿灯状态，便于违章判定
        if (straightState == LightState.STRAIGHT_GREEN) {
            return straightState;
        } else if (turnState == LightState.TURN_GREEN) {
            return turnState;
        } else if (straightState == LightState.STRAIGHT_YELLOW) {
            return straightState;
        } else if (turnState == LightState.TURN_YELLOW) {
            return turnState;
        } else {
            // 默认返回综合红灯状态
            return LightState.RED;
        }
    }

    /**
     * 判断在指定时间是否为红灯状态
     * 
     * @param intersectionId 路口ID
     * @param checkTime      检查时间
     * @return true-红灯，false-非红灯
     */
    public boolean isRedLight(Long intersectionId, LocalDateTime checkTime) {
        return getCurrentLightState(intersectionId, checkTime) == LightState.RED;
    }

    /**
     * 判断在指定时间是否为绿灯状态
     */
    public boolean isGreenLight(Long intersectionId, LocalDateTime checkTime) {
        return getCurrentLightState(intersectionId, checkTime) == LightState.GREEN;
    }

    /**
     * 判断在指定时间是否为黄灯状态
     */
    public boolean isYellowLight(Long intersectionId, LocalDateTime checkTime) {
        return getCurrentLightState(intersectionId, checkTime) == LightState.YELLOW;
    }

    /**
     * 判断在指定时间是否为直行绿灯状态
     */
    public boolean isStraightGreenLight(Long intersectionId, LocalDateTime checkTime) {
        var state = getCurrentLightState(intersectionId, checkTime);
        return state == LightState.STRAIGHT_GREEN || state == LightState.GREEN;
    }

    /**
     * 判断在指定时间是否为转弯绿灯状态
     */
    public boolean isTurnGreenLight(Long intersectionId, LocalDateTime checkTime) {
        return getCurrentLightState(intersectionId, checkTime) == LightState.TURN_GREEN;
    }

    /**
     * 判断在指定时间是否为转弯红灯状态
     */
    public boolean isTurnRedLight(Long intersectionId, LocalDateTime checkTime) {
        var state = getCurrentLightState(intersectionId, checkTime);
        return state == LightState.TURN_RED || state == LightState.RED;
    }

    /**
     * 判断在指定时间是否为转弯黄灯状态
     */
    public boolean isTurnYellowLight(Long intersectionId, LocalDateTime checkTime) {
        var state = getCurrentLightState(intersectionId, checkTime);
        return state == LightState.TURN_YELLOW || state == LightState.YELLOW;
    }

    /**
     * 判断在指定时间是否为直行红灯状态
     */
    public boolean isStraightRedLight(Long intersectionId, LocalDateTime checkTime) {
        var state = getCurrentLightState(intersectionId, checkTime);
        return state == LightState.STRAIGHT_RED || state == LightState.RED;
    }

    /**
     * 判断在指定时间是否为直行黄灯状态
     */
    public boolean isStraightYellowLight(Long intersectionId, LocalDateTime checkTime) {
        var state = getCurrentLightState(intersectionId, checkTime);
        return state == LightState.STRAIGHT_YELLOW || state == LightState.YELLOW;
    }

    /**
     * 模拟设置红绿灯状态（用于测试）
     * 
     * @param intersectionId  路口ID
     * @param lightState      灯色状态
     * @param durationSeconds 持续时间（秒）
     */
    public void simulateLightState(Long intersectionId, LightState lightState, int durationSeconds) {
        String cacheKey = SIGNAL_STATE_CACHE_PREFIX + intersectionId;
        redisTemplate.opsForValue().set(cacheKey, lightState.name(),
                durationSeconds, TimeUnit.SECONDS);

        System.out.println(String.format("路口%d模拟设置为%s状态，持续%d秒",
                intersectionId, lightState.name(), durationSeconds));
    }

    /**
     * 清除指定路口的状态缓存
     */
    public void clearLightStateCache(Long intersectionId) {
        String cacheKey = SIGNAL_STATE_CACHE_PREFIX + intersectionId;
        redisTemplate.delete(cacheKey);
    }
}