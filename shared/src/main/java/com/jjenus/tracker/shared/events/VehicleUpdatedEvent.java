package com.jjenus.tracker.shared.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jjenus.tracker.shared.pubsub.DomainEvent;
import com.jjenus.tracker.shared.domain.LocationPoint;

import java.util.Map;

public class VehicleUpdatedEvent extends DomainEvent {
    private final String vehicleId;
    private final LocationPoint newLocation;
    private final Map<String, Object> metaData;


    @JsonCreator
    public VehicleUpdatedEvent(@JsonProperty("vehicleId")String vehicleId, @JsonProperty("newLocation") LocationPoint newLocation, Map<String, Object> metaData) {
        this.vehicleId = vehicleId;
        this.newLocation = newLocation;
        this.metaData = metaData;
    }

    public String getVehicleId() {
        return vehicleId;
    }
    public Map<String, Object> getMetaData() { return metaData; }
    public LocationPoint getNewLocation() { return newLocation; }
}
