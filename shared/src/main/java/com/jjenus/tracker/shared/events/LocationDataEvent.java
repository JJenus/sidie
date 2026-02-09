package com.jjenus.tracker.shared.events;

import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.shared.pubsub.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.HashMap;

public class LocationDataEvent extends DomainEvent {
    private final String deviceId;
    private final LocationPoint location;
    private final String protocol;
    private final String rawData;

    @JsonCreator
    public LocationDataEvent(
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("location") LocationPoint location,
            @JsonProperty("protocol") String protocol,
            @JsonProperty("rawData") String rawData) {
        super(); // This calls DomainEvent constructor
        this.deviceId = deviceId;
        this.location = location;
        this.protocol = protocol;
        this.rawData = rawData;
    }

    // Getters
    public String getDeviceId() { return deviceId; }
    public LocationPoint getLocation() { return location; }
    public String getProtocol() { return protocol; }
}