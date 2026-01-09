package com.jjenus.tracker.shared.domain;

import com.jjenus.tracker.shared.pubsub.DomainEvent;
import java.util.Map;
import java.util.HashMap;

public class LocationDataEvent extends DomainEvent {
    private final String deviceId;
    private final LocationPoint location;
    private final String protocol;
    private final Map<String, Object> metaData;
    
    public LocationDataEvent(String deviceId, LocationPoint location, String protocol) {
        this.deviceId = deviceId;
        this.location = location;
        this.protocol = protocol;
        this.metaData = new HashMap<>();
    }
    
    public LocationDataEvent(String deviceId, LocationPoint location, String protocol, private final Map<String, Object> metaData) {
        this.deviceId = deviceId;
        this.location = location;
        this.protocol = protocol;
        this.metaData = metaData;
    }

    public String getDeviceId() { return deviceId; }
    public LocationPoint getLocation() { return location; }
    public String getProtocol() { return protocol; }
    public String getMetaData() { return protocol; }
}
