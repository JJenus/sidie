package com.jjenus.tracker.shared.redis;

import com.jjenus.tracker.shared.domain.ConnectionInfo;
import com.jjenus.tracker.shared.domain.ConnectionMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import reactor.netty.Connection;

@Component
public class RedisConnectionTracker {
    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionTracker.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOps;

    // In-memory storage for live Netty connections
    private final Map<String, Connection> liveConnections = new ConcurrentHashMap<>();

    public RedisConnectionTracker(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
    }

    public void registerConnection(String connectionId, String deviceId, String clientIp,
                                   Connection nettyConnection) {
        // Store metadata in Redis (serializable data only)
        ConnectionMetadata metadata = new ConnectionMetadata(
                connectionId,
                deviceId,
                clientIp,
                Instant.now(),
                Instant.now()
        );

        // Store metadata in Redis with TTL
        String key = getConnectionKey(connectionId);
        valueOps.set(key, metadata, Duration.ofHours(1));

        // Store reverse lookup: deviceId -> connectionId
        if (!"unknown".equals(deviceId)) {
            String deviceKey = getDeviceConnectionKey(deviceId);
            valueOps.set(deviceKey, connectionId, Duration.ofHours(1));
        }

        // Store live connection in memory
        liveConnections.put(connectionId, nettyConnection);

        // Add disconnect listener to clean up
        nettyConnection.onDispose(() -> {
            liveConnections.remove(connectionId);
            logger.debug("Live connection removed for ID: {}", connectionId);
        });

        logger.info("Registered connection {} for device {}", connectionId, deviceId);
    }

    public void updateConnectionDevice(String connectionId, String deviceId) {
        ConnectionMetadata metadata = getConnectionMetadata(connectionId);
        if (metadata != null) {
            metadata.setDeviceId(deviceId);
            metadata.setLastSeen(Instant.now());

            // Update Redis
            String key = getConnectionKey(connectionId);
            valueOps.set(key, metadata, Duration.ofHours(1));

            // Update reverse lookup
            String deviceKey = getDeviceConnectionKey(deviceId);
            valueOps.set(deviceKey, connectionId, Duration.ofHours(1));

            logger.info("Updated connection {} with device ID {}", connectionId, deviceId);
        }
    }

    public void updateLastSeen(String connectionId) {
        ConnectionMetadata metadata = getConnectionMetadata(connectionId);
        if (metadata != null) {
            metadata.setLastSeen(Instant.now());

            // Update Redis
            String key = getConnectionKey(connectionId);
            valueOps.set(key, metadata, Duration.ofHours(1));
        }
    }

    public void removeConnection(String connectionId) {
        // Get metadata before removing
        ConnectionMetadata metadata = getConnectionMetadata(connectionId);

        // Remove from Redis
        String key = getConnectionKey(connectionId);
        redisTemplate.delete(key);

        // Remove reverse lookup if we have device ID
        if (metadata != null && metadata.getDeviceId() != null &&
                !"unknown".equals(metadata.getDeviceId())) {
            String deviceKey = getDeviceConnectionKey(metadata.getDeviceId());
            redisTemplate.delete(deviceKey);
        }

        // Remove live connection
        Connection liveConnection = liveConnections.remove(connectionId);
        if (liveConnection != null && !liveConnection.isDisposed()) {
            liveConnection.disposeNow();
        }

        logger.info("Removed connection {}", connectionId);
    }

    public ConnectionInfo getConnectionInfo(String connectionId) {
        // Get metadata from Redis
        ConnectionMetadata metadata = getConnectionMetadata(connectionId);
        if (metadata == null) {
            return null;
        }

        // Get live connection from memory
        Connection nettyConnection = liveConnections.get(connectionId);

        // Combine into ConnectionInfo
        return new ConnectionInfo(
                metadata.getConnectionId(),
                metadata.getDeviceId(),
                metadata.getClientIp(),
                metadata.getConnectedAt(),
                metadata.getLastSeen(),
                nettyConnection
        );
    }

    public ConnectionInfo getConnectionByDeviceId(String deviceId) {
        // First get connectionId from reverse lookup
        String deviceKey = getDeviceConnectionKey(deviceId);
        Object value = valueOps.get(deviceKey);

        if (value instanceof String connectionId) {
            return getConnectionInfo(connectionId);
        }

        return null;
    }

    public Connection getLiveConnection(String connectionId) {
        return liveConnections.get(connectionId);
    }

    public int getActiveConnectionCount() {
        // Count from Redis for accuracy across instances
        String pattern = getConnectionKey("*");
        Long count = redisTemplate.countExistingKeys(redisTemplate.keys(pattern));
        return count != null ? count.intValue() : 0;
    }

    public int getLiveConnectionCount() {
        return liveConnections.size();
    }

    public Map<String, ConnectionInfo> getAllConnections() {
        Map<String, ConnectionInfo> connections = new ConcurrentHashMap<>();

        String pattern = getConnectionKey("*");
        redisTemplate.keys(pattern).forEach(key -> {
            Object value = valueOps.get(key);
            if (value instanceof ConnectionMetadata metadata) {
                Connection nettyConnection = liveConnections.get(metadata.getConnectionId());
                ConnectionInfo info = new ConnectionInfo(
                        metadata.getConnectionId(),
                        metadata.getDeviceId(),
                        metadata.getClientIp(),
                        metadata.getConnectedAt(),
                        metadata.getLastSeen(),
                        nettyConnection
                );
                connections.put(metadata.getConnectionId(), info);
            }
        });

        return connections;
    }

    public void cleanupStaleConnections() {
        // Clean up connections where Redis entry exists but no live connection
        String pattern = getConnectionKey("*");
        redisTemplate.keys(pattern).forEach(key -> {
            Object value = valueOps.get(key);
            if (value instanceof ConnectionMetadata metadata) {
                String connectionId = metadata.getConnectionId();
                if (!liveConnections.containsKey(connectionId)) {
                    // Connection is stale, remove from Redis
                    redisTemplate.delete(key);
                    logger.info("Cleaned up stale connection: {}", connectionId);

                    // Clean up reverse lookup if needed
                    String deviceKey = getDeviceConnectionKey(metadata.getDeviceId());
                    redisTemplate.delete(deviceKey);
                }
            }
        });
    }

    private String getConnectionKey(String connectionId) {
        return "tracker:connection:" + connectionId;
    }

    private String getDeviceConnectionKey(String deviceId) {
        return "tracker:device:connection:" + deviceId;
    }

    private ConnectionMetadata getConnectionMetadata(String connectionId) {
        String key = getConnectionKey(connectionId);
        Object value = valueOps.get(key);

        if (value instanceof ConnectionMetadata) {
            return (ConnectionMetadata) value;
        }
        return null;
    }
}