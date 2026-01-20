package com.jjenus.tracker.shared.domain;

import com.jjenus.tracker.shared.pubsub.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.HashMap;

public class LocationDataEvent extends DomainEvent {
    private final String deviceId;
    private final LocationPoint location;
    private final String protocol;
    private final Map<String, Object> metaData;

    @JsonCreator
    public LocationDataEvent(
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("location") LocationPoint location,
            @JsonProperty("protocol") String protocol,
            @JsonProperty("metaData") Map<String, Object> metaData) {
        super(); // This calls DomainEvent constructor
        this.deviceId = deviceId;
        this.location = location;
        this.protocol = protocol;
        this.metaData = metaData != null ? metaData : new HashMap<>();
    }

    // Convenience constructor
    public LocationDataEvent(String deviceId, LocationPoint location, String protocol) {
        this(deviceId, location, protocol, new HashMap<>());
    }

    // Getters
    public String getDeviceId() { return deviceId; }
    public LocationPoint getLocation() { return location; }
    public String getProtocol() { return protocol; }
    public Map<String, Object> getMetaData() { return metaData; }

    public void addMetaData(String key, Object value) {
        this.metaData.put(key, value);
    }
}