package com.jjenus.tracker.shared.domain;

import com.jjenus.tracker.shared.pubsub.DomainEvent;

public class LocationDataEvent extends DomainEvent {
    private final String deviceId;
    private final LocationPoint location;
    private final String protocol;
    
    public LocationDataEvent(String deviceId, LocationPoint location, String protocol) {
        this.deviceId = deviceId;
        this.location = location;
        this.protocol = protocol;
    }

    public String getDeviceId() { return deviceId; }
    public LocationPoint getLocation() { return location; }
    public String getProtocol() { return protocol; }
}
