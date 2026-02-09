package com.jjenus.tracker.shared.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jjenus.tracker.shared.pubsub.DomainEvent;
import com.jjenus.tracker.shared.domain.LocationPoint;

import java.util.Map;

public class VehicleUpdatedEvent extends DomainEvent {
    private final String vehicleId;
    private final LocationPoint newLocation;
    private final String trackerId;


    @JsonCreator
    public VehicleUpdatedEvent(@JsonProperty("vehicleId") String vehicleId, @JsonProperty("newLocation") LocationPoint newLocation, String trackerId) {
        this.vehicleId = vehicleId;
        this.newLocation = newLocation;
        this.trackerId = trackerId;
    }

    public String getVehicleId() {
        return vehicleId;
    }
    public String getTrackerId() { return trackerId; }
    public LocationPoint getNewLocation() { return newLocation; }
}
