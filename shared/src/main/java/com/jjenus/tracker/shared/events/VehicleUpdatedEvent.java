package com.jjenus.tracker.shared.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jjenus.tracker.shared.pubsub.DomainEvent;
import com.jjenus.tracker.shared.domain.LocationPoint;

public class VehicleUpdatedEvent extends DomainEvent {
    private final String vehicleId;
    private final LocationPoint newLocation;

    @JsonCreator
    public VehicleUpdatedEvent(@JsonProperty("vehicleId")String vehicleId,  @JsonProperty("newLocation") LocationPoint newLocation) {
        this.vehicleId = vehicleId;
        this.newLocation = newLocation;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public LocationPoint getNewLocation() { return newLocation; }
}
