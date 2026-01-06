package com.jjenus.tracker.shared.domain;

import java.time.Instant;

public class ConnectionInfo {
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

