package com.jjenus.tracker.core.domain;

import com.jjenus.tracker.shared.pubsub.DomainEvent;
import com.jjenus.tracker.shared.domain.LocationPoint;

public class VehicleUpdatedEvent extends DomainEvent {
    private final Vehicle vehicle;
    private final LocationPoint newLocation;

    public VehicleUpdatedEvent(Vehicle vehicle, LocationPoint newLocation) {
        this.vehicle = vehicle;
        this.newLocation = newLocation;
    }

    public Vehicle getVehicle() { return vehicle; }
    public LocationPoint getNewLocation() { return newLocation; }
}
