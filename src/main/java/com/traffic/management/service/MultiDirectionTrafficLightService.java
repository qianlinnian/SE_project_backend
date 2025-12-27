package com.traffic.management.service;

import com.traffic.management.entity.IntersectionDirection;
import com.traffic.management.entity.TrafficPhase;
import com.traffic.management.entity.Violation;
import com.traffic.management.repository.IntersectionDirectionRepository;
import com.traffic.management.repository.TrafficPhaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 多方向交通信号灯状态服务
 * 支持每个路口四个方向的独立信号控制
 */
@Service
public class MultiDirectionTrafficLightService {

    @Autowired
    private IntersectionDirectionRepository intersectionDirectionRepository;

    @Autowired
    private TrafficPhaseRepository trafficPhaseRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String DIRECTION_STATE_CACHE_PREFIX = "traffic:direction:";
    private static final String PHASE_STATE_CACHE_PREFIX = "traffic:phase:";
    private static final long CACHE_DURATION_SECONDS = 10;

    /**
     * 详细的信号灯状态结果
     */
    public static class DirectionLightState implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public IntersectionDirection.LightPhase straightPhase;
        public IntersectionDirection.LightPhase leftTurnPhase;
        public IntersectionDirection.LightPhase rightTurnPhase;
        public int straightRemaining;
        public int leftTurnRemaining;
        public int rightTurnRemaining;

        // 无参构造函数，用于序列化
        public DirectionLightState() {
        }

        public DirectionLightState(IntersectionDirection.LightPhase straightPhase,
                IntersectionDirection.LightPhase leftTurnPhase,
                IntersectionDirection.LightPhase rightTurnPhase,
                int straightRemaining, int leftTurnRemaining, int rightTurnRemaining) {
            this.straightPhase = straightPhase;
            this.leftTurnPhase = leftTurnPhase;
            this.rightTurnPhase = rightTurnPhase;
            this.straightRemaining = straightRemaining;
            this.leftTurnRemaining = leftTurnRemaining;
            this.rightTurnRemaining = rightTurnRemaining;
        }
    }

    /**
     * 获取指定路口指定方向的当前信号灯状态
     *
     * @param intersectionId 路口ID
     * @param direction      方向
     * @param turnType       转弯类型
     * @param checkTime      检查时间
     * @return 信号灯状态
     */
    public IntersectionDirection.LightPhase getCurrentLightState(Long intersectionId,
            Violation.Direction direction,
            Violation.TurnType turnType,
            LocalDateTime checkTime) {
        // 从缓存获取方向配置
        String cacheKey = DIRECTION_STATE_CACHE_PREFIX + intersectionId + ":" + direction.name();
        DirectionLightState cachedState = (DirectionLightState) redisTemplate.opsForValue().get(cacheKey);

        if (cachedState == null) {
            // 从数据库获取并计算当前状态
            cachedState = calculateDirectionLightState(intersectionId, convertToIntersectionDirection(direction),
                    checkTime);

            // 缓存结果
            if (cachedState != null) {
                redisTemplate.opsForValue().set(cacheKey, cachedState, CACHE_DURATION_SECONDS, TimeUnit.SECONDS);
            }
        }

        if (cachedState == null) {
            return IntersectionDirection.LightPhase.RED; // 默认红灯（安全策略）
        }

        // 根据转弯类型返回对应的信号灯状态
        switch (turnType) {
            case STRAIGHT:
                return cachedState.straightPhase;
            case LEFT_TURN:
                return cachedState.leftTurnPhase;
            case RIGHT_TURN:
                return cachedState.rightTurnPhase;
            case U_TURN:
                return cachedState.leftTurnPhase; // 掉头通常跟随左转信号
            default:
                return IntersectionDirection.LightPhase.RED;
        }
    }

    /**
     * 计算指定方向的详细信号灯状态
     */
    private DirectionLightState calculateDirectionLightState(Long intersectionId,
            IntersectionDirection.Direction direction,
            LocalDateTime checkTime) {
        Optional<IntersectionDirection> directionConfig = intersectionDirectionRepository
                .findByIntersectionIdAndDirection(intersectionId, direction);

        if (directionConfig.isEmpty()) {
            return null;
        }

        IntersectionDirection config = directionConfig.get();

        // 计算当前各个信号灯的状态
        // 这里简化处理，实际应该根据相位配置和时间计算
        int secondsOfDay = checkTime.getHour() * 3600 + checkTime.getMinute() * 60 + checkTime.getSecond();

        // 直行信号灯计算
        int straightCycle = config.getStraightRedDuration() +
                config.getStraightYellowDuration() +
                config.getStraightGreenDuration();
        int straightPosition = secondsOfDay % straightCycle;

        IntersectionDirection.LightPhase straightPhase;
        int straightRemaining;
        if (straightPosition < config.getStraightRedDuration()) {
            straightPhase = IntersectionDirection.LightPhase.RED;
            straightRemaining = config.getStraightRedDuration() - straightPosition;
        } else if (straightPosition < config.getStraightRedDuration() + config.getStraightGreenDuration()) {
            straightPhase = IntersectionDirection.LightPhase.GREEN;
            straightRemaining = config.getStraightRedDuration() + config.getStraightGreenDuration() - straightPosition;
        } else {
            straightPhase = IntersectionDirection.LightPhase.YELLOW;
            straightRemaining = straightCycle - straightPosition;
        }

        // 左转信号灯计算
        int leftTurnCycle = config.getLeftTurnRedDuration() +
                config.getLeftTurnYellowDuration() +
                config.getLeftTurnGreenDuration();
        int leftTurnPosition = (secondsOfDay + config.getStraightGreenDuration()) % leftTurnCycle; // 错开时序

        IntersectionDirection.LightPhase leftTurnPhase;
        int leftTurnRemaining;
        if (leftTurnPosition < config.getLeftTurnRedDuration()) {
            leftTurnPhase = IntersectionDirection.LightPhase.RED;
            leftTurnRemaining = config.getLeftTurnRedDuration() - leftTurnPosition;
        } else if (leftTurnPosition < config.getLeftTurnRedDuration() + config.getLeftTurnGreenDuration()) {
            leftTurnPhase = IntersectionDirection.LightPhase.GREEN;
            leftTurnRemaining = config.getLeftTurnRedDuration() + config.getLeftTurnGreenDuration() - leftTurnPosition;
        } else {
            leftTurnPhase = IntersectionDirection.LightPhase.YELLOW;
            leftTurnRemaining = leftTurnCycle - leftTurnPosition;
        }

        // 右转信号灯计算（类似左转）
        int rightTurnCycle = config.getRightTurnRedDuration() +
                config.getRightTurnYellowDuration() +
                config.getRightTurnGreenDuration();
        int rightTurnPosition = (secondsOfDay + config.getStraightGreenDuration() * 2) % rightTurnCycle;

        IntersectionDirection.LightPhase rightTurnPhase;
        int rightTurnRemaining;
        if (rightTurnPosition < config.getRightTurnRedDuration()) {
            rightTurnPhase = IntersectionDirection.LightPhase.RED;
            rightTurnRemaining = config.getRightTurnRedDuration() - rightTurnPosition;
        } else if (rightTurnPosition < config.getRightTurnRedDuration() + config.getRightTurnGreenDuration()) {
            rightTurnPhase = IntersectionDirection.LightPhase.GREEN;
            rightTurnRemaining = config.getRightTurnRedDuration() + config.getRightTurnGreenDuration()
                    - rightTurnPosition;
        } else {
            rightTurnPhase = IntersectionDirection.LightPhase.YELLOW;
            rightTurnRemaining = rightTurnCycle - rightTurnPosition;
        }

        return new DirectionLightState(straightPhase, leftTurnPhase, rightTurnPhase,
                straightRemaining, leftTurnRemaining, rightTurnRemaining);
    }

    /**
     * 验证违章行为是否在当前信号灯状态下构成违法
     *
     * @param intersectionId 路口ID
     * @param direction      方向
     * @param turnType       转弯类型
     * @param violationType  违章类型
     * @param violationTime  违章时间
     * @return true-构成违章，false-不构成违章
     */
    public boolean validateViolationWithMultiDirectionLight(Long intersectionId,
            Violation.Direction direction,
            Violation.TurnType turnType,
            Violation.ViolationType violationType,
            LocalDateTime violationTime) {
        switch (violationType) {
            case RED_LIGHT:
                // 闯红灯：对应转弯类型的信号灯必须是红灯才构成违章
                var lightState = getCurrentLightState(intersectionId, direction, turnType, violationTime);
                return lightState == IntersectionDirection.LightPhase.RED;

            case ILLEGAL_TURN:
                // 违法转弯：对应转弯类型的信号灯不是绿灯就构成违章
                var turnLightState = getCurrentLightState(intersectionId, direction, turnType, violationTime);
                return turnLightState != IntersectionDirection.LightPhase.GREEN;

            case WRONG_WAY:
            case CROSS_SOLID_LINE:
                // 逆行、跨实线：不受红绿灯状态影响，始终构成违章
                return true;

            default:
                return true;
        }
    }

    /**
     * 获取路口所有方向的当前状态概览
     *
     * @param intersectionId 路口ID
     * @return 各方向状态映射
     */
    public Map<String, DirectionLightState> getIntersectionAllDirectionsStatus(Long intersectionId) {
        List<IntersectionDirection> directions = intersectionDirectionRepository.findByIntersectionId(intersectionId);
        LocalDateTime now = LocalDateTime.now();

        // 使用HashMap允许null值，并为未配置的方向提供默认状态
        Map<String, DirectionLightState> result = new java.util.HashMap<>();

        DirectionLightState eastState = calculateDirectionLightState(intersectionId,
                IntersectionDirection.Direction.EAST, now);
        DirectionLightState southState = calculateDirectionLightState(intersectionId,
                IntersectionDirection.Direction.SOUTH, now);
        DirectionLightState westState = calculateDirectionLightState(intersectionId,
                IntersectionDirection.Direction.WEST, now);
        DirectionLightState northState = calculateDirectionLightState(intersectionId,
                IntersectionDirection.Direction.NORTH, now);

        // 如果方向配置不存在，使用默认红灯状态
        DirectionLightState defaultState = new DirectionLightState(
                IntersectionDirection.LightPhase.RED,
                IntersectionDirection.LightPhase.RED,
                IntersectionDirection.LightPhase.RED,
                0, 0, 0);

        result.put("EAST", eastState != null ? eastState : defaultState);
        result.put("SOUTH", southState != null ? southState : defaultState);
        result.put("WEST", westState != null ? westState : defaultState);
        result.put("NORTH", northState != null ? northState : defaultState);

        return result;
    }

    /**
     * 模拟设置指定方向指定转弯类型的信号灯状态
     *
     * @param intersectionId  路口ID
     * @param direction       方向
     * @param turnType        转弯类型
     * @param lightPhase      信号灯状态
     * @param durationSeconds 持续时间
     */
    public void simulateDirectionLightState(Long intersectionId,
            Violation.Direction direction,
            Violation.TurnType turnType,
            IntersectionDirection.LightPhase lightPhase,
            int durationSeconds) {
        String cacheKey = DIRECTION_STATE_CACHE_PREFIX + intersectionId + ":" + direction.name();

        // 创建模拟状态
        DirectionLightState simulatedState = createSimulatedState(turnType, lightPhase, durationSeconds);

        redisTemplate.opsForValue().set(cacheKey, simulatedState, durationSeconds, TimeUnit.SECONDS);
    }

    private DirectionLightState createSimulatedState(Violation.TurnType turnType,
            IntersectionDirection.LightPhase lightPhase,
            int durationSeconds) {
        // 默认其他转弯类型为红灯，只有指定的转弯类型设置为指定状态
        IntersectionDirection.LightPhase straightPhase = IntersectionDirection.LightPhase.RED;
        IntersectionDirection.LightPhase leftTurnPhase = IntersectionDirection.LightPhase.RED;
        IntersectionDirection.LightPhase rightTurnPhase = IntersectionDirection.LightPhase.RED;

        switch (turnType) {
            case STRAIGHT:
                straightPhase = lightPhase;
                break;
            case LEFT_TURN:
            case U_TURN:
                leftTurnPhase = lightPhase;
                break;
            case RIGHT_TURN:
                rightTurnPhase = lightPhase;
                break;
        }

        return new DirectionLightState(straightPhase, leftTurnPhase, rightTurnPhase,
                durationSeconds, durationSeconds, durationSeconds);
    }

    /**
     * 转换违章实体的方向枚举到路口方向实体的方向枚举
     */
    private IntersectionDirection.Direction convertToIntersectionDirection(Violation.Direction direction) {
        switch (direction) {
            case EAST:
                return IntersectionDirection.Direction.EAST;
            case SOUTH:
                return IntersectionDirection.Direction.SOUTH;
            case WEST:
                return IntersectionDirection.Direction.WEST;
            case NORTH:
                return IntersectionDirection.Direction.NORTH;
            default:
                return IntersectionDirection.Direction.EAST;
        }
    }

    /**
     * 清除指定路口的状态缓存
     */
    public void clearIntersectionCache(Long intersectionId) {
        String[] directions = { "EAST", "SOUTH", "WEST", "NORTH" };
        for (String direction : directions) {
            String cacheKey = DIRECTION_STATE_CACHE_PREFIX + intersectionId + ":" + direction;
            redisTemplate.delete(cacheKey);
        }
    }
}