package com.jjenus.tracker.shared.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.shared.domain.ConnectionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class RedisConnectionTracker {
    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionTracker.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOps;

    public RedisConnectionTracker(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    public void registerConnection(String connectionId, String deviceId, String clientIp,
                                   reactor.netty.Connection nettyConnection) {
        ConnectionInfo info = new ConnectionInfo(connectionId, deviceId, clientIp, nettyConnection);

        // Store in Redis with TTL
        String key = getConnectionKey(connectionId);
        valueOps.set(key, info, Duration.ofHours(1));

        // Also store reverse lookup: deviceId -> connectionId
        if (!"unknown".equals(deviceId)) {
            String deviceKey = getDeviceConnectionKey(deviceId);
            valueOps.set(deviceKey, connectionId, Duration.ofHours(1));
        }

        logger.info("Registered connection {} for device {}", connectionId, deviceId);
    }

    public void updateConnectionDevice(String connectionId, String deviceId) {
        ConnectionInfo info = getConnectionInfo(connectionId);
        if (info != null) {
            info.setDeviceId(deviceId);

            // Update Redis
            String key = getConnectionKey(connectionId);
            valueOps.set(key, info, Duration.ofHours(1));

            // Update reverse lookup
            String deviceKey = getDeviceConnectionKey(deviceId);
            valueOps.set(deviceKey, connectionId, Duration.ofHours(1));

            logger.info("Updated connection {} with device ID {}", connectionId, deviceId);
        }
    }

    public void updateLastSeen(String connectionId) {
        ConnectionInfo info = getConnectionInfo(connectionId);
        if (info != null) {
            info.updateLastSeen();

            // Update Redis
            String key = getConnectionKey(connectionId);
            valueOps.set(key, info, Duration.ofHours(1));
        }
    }

    public void removeConnection(String connectionId) {
        // Get info before removing
        ConnectionInfo info = getConnectionInfo(connectionId);

        // Remove from Redis
        String key = getConnectionKey(connectionId);
        redisTemplate.delete(key);

        // Remove reverse lookup if we have device ID
        if (info != null && info.getDeviceId() != null && !"unknown".equals(info.getDeviceId())) {
            String deviceKey = getDeviceConnectionKey(info.getDeviceId());
            redisTemplate.delete(deviceKey);
        }

        logger.info("Removed connection {}", connectionId);
    }

    public ConnectionInfo getConnectionInfo(String connectionId) {
        String key = getConnectionKey(connectionId);
        Object value = valueOps.get(key);

        if (value instanceof ConnectionInfo) {
            return (ConnectionInfo) value;
        }
        return null;
    }

    public ConnectionInfo getConnectionByDeviceId(String deviceId) {
        // First get connectionId from reverse lookup
        String deviceKey = getDeviceConnectionKey(deviceId);
        Object value = valueOps.get(deviceKey);

        if (value instanceof String) {
            return getConnectionInfo((String) value);
        }

        return null;
    }

    public int getActiveConnectionCount() {
        // Count from Redis for accuracy across instances
        String pattern = getConnectionKey("*");
        Long count = redisTemplate.countExistingKeys(redisTemplate.keys(pattern));
        return count != null ? count.intValue() : 0;
    }

    public Map<String, ConnectionInfo> getAllConnections() {
        Map<String, ConnectionInfo> connections = new HashMap<>();

        String pattern = getConnectionKey("*");
        redisTemplate.keys(pattern).forEach(key -> {
            Object value = valueOps.get(key);
            if (value instanceof ConnectionInfo info) {
                connections.put(info.getConnectionId(), info);
            }
        });

        return connections;
    }

    private String getConnectionKey(String connectionId) {
        return "tracker:connection:" + connectionId;
    }

    private String getDeviceConnectionKey(String deviceId) {
        return "tracker:device:connection:" + deviceId;
    }
}