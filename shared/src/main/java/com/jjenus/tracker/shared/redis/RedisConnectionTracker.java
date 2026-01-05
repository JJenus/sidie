package com.jjenus.tracker.shared.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisConnectionTracker {
    private static final Logger logger = LoggerFactory.getLogger(RedisConnectionTracker.class);
    
    private final RedisTemplate<String, ConnectionInfo> redisTemplate;
    private final ValueOperations<String, ConnectionInfo> valueOps;
    private final ObjectMapper objectMapper;
    
    // Local cache for fast access (optional, Redis is already fast)
    private final Map<String, ConnectionInfo> localConnectionCache = new ConcurrentHashMap<>();
    
    public RedisConnectionTracker(RedisTemplate<String, ConnectionInfo> redisTemplate, 
                                 ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.valueOps = redisTemplate.opsForValue();
        this.objectMapper = objectMapper;
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
            redisTemplate.opsForValue().set(deviceKey, connectionId, Duration.ofHours(1));
        }
        
        // Update local cache
        localConnectionCache.put(connectionId, info);
        
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
            redisTemplate.opsForValue().set(deviceKey, connectionId, Duration.ofHours(1));
            
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
        
        // Remove from local cache
        localConnectionCache.remove(connectionId);
        
        logger.info("Removed connection {}", connectionId);
    }
    
    public ConnectionInfo getConnectionInfo(String connectionId) {
        // Try local cache first
        ConnectionInfo info = localConnectionCache.get(connectionId);
        if (info != null) {
            return info;
        }
        
        // Fall back to Redis
        String key = getConnectionKey(connectionId);
        info = valueOps.get(key);
        
        if (info != null) {
            localConnectionCache.put(connectionId, info);
        }
        
        return info;
    }
    
    public ConnectionInfo getConnectionByDeviceId(String deviceId) {
        // First get connectionId from reverse lookup
        String deviceKey = getDeviceConnectionKey(deviceId);
        String connectionId = redisTemplate.opsForValue().get(deviceKey);
        
        if (connectionId != null) {
            return getConnectionInfo(connectionId);
        }
        
        return null;
    }
    
    public int getActiveConnectionCount() {
        // Count from Redis for accuracy across instances
        String pattern = getConnectionKey("*");
        Long count = redisTemplate.countExistingKeys(redisTemplate.keys(pattern));
        return count != null ? count.intValue() : localConnectionCache.size();
    }
    
    public Map<String, ConnectionInfo> getAllConnections() {
        // Refresh local cache from Redis
        localConnectionCache.clear();
        
        String pattern = getConnectionKey("*");
        redisTemplate.keys(pattern).forEach(key -> {
            ConnectionInfo info = valueOps.get(key);
            if (info != null) {
                localConnectionCache.put(info.getConnectionId(), info);
            }
        });
        
        return Map.copyOf(localConnectionCache);
    }
    
    private String getConnectionKey(String connectionId) {
        return "tracker:connection:" + connectionId;
    }
    
    private String getDeviceConnectionKey(String deviceId) {
        return "tracker:device:connection:" + deviceId;
    }
    
    // ConnectionInfo is now a proper class, not JSON string
    public static class ConnectionInfo {
        private String connectionId;
        private String deviceId;
        private String clientIp;
        private Instant connectedAt;
        private Instant lastSeen;
        private transient reactor.netty.Connection nettyConnection; // Transient = not serialized
        
        // Default constructor for Jackson
        public ConnectionInfo() {}
        
        public ConnectionInfo(String connectionId, String deviceId, String clientIp, 
                             reactor.netty.Connection nettyConnection) {
            this.connectionId = connectionId;
            this.deviceId = deviceId;
            this.clientIp = clientIp;
            this.nettyConnection = nettyConnection;
            this.connectedAt = Instant.now();
            this.lastSeen = this.connectedAt;
        }
        
        public void updateLastSeen() {
            this.lastSeen = Instant.now();
        }
        
        // Getters and Setters (required for Jackson)
        public String getConnectionId() { return connectionId; }
        public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
        
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        
        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }
        
        public Instant getConnectedAt() { return connectedAt; }
        public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }
        
        public Instant getLastSeen() { return lastSeen; }
        public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
        
        public reactor.netty.Connection getNettyConnection() { return nettyConnection; }
        public void setNettyConnection(reactor.netty.Connection nettyConnection) { 
            this.nettyConnection = nettyConnection; 
        }
        
        @Override
        public String toString() {
            return "ConnectionInfo{" +
                    "connectionId='" + connectionId + '\'' +
                    ", deviceId='" + deviceId + '\'' +
                    ", clientIp='" + clientIp + '\'' +
                    ", connectedAt=" + connectedAt +
                    ", lastSeen=" + lastSeen +
                    '}';
        }
    }
}