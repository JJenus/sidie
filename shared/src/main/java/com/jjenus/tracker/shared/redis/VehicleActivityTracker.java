package com.jjenus.tracker.shared.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class VehicleActivityTracker {

    private static final Logger logger = LoggerFactory.getLogger(VehicleActivityTracker.class);
    private static final String KEY_PREFIX = "vehicle:lastseen:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOps;

    public VehicleActivityTracker(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    public void recordActivity(String vehicleId, Instant timestamp) {
        try {
            String key = KEY_PREFIX + vehicleId;
            valueOps.set(key, timestamp.toEpochMilli());
            redisTemplate.expire(key, Duration.ofHours(24));
        } catch (Exception e) {
            logger.warn("Failed to record activity for vehicle {}: {}", vehicleId, e.getMessage());
        }
    }

    public Optional<Instant> getLastSeen(String vehicleId) {
        try {
            String key = KEY_PREFIX + vehicleId;
            Object value = valueOps.get(key);
            if (value instanceof Number n) {
                return Optional.of(Instant.ofEpochMilli(n.longValue()));
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("Failed to get lastSeen for vehicle {}: {}", vehicleId, e.getMessage());
            return Optional.empty();
        }
    }

    public Set<String> getAllActiveVehicleIds() {
        Set<String> result = new HashSet<>();
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    result.add(key.substring(KEY_PREFIX.length()));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to get active vehicle IDs: {}", e.getMessage());
        }
        return result;
    }
}
