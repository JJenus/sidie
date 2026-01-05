package com.jjenus.tracker.core.domain;

import com.jjenus.tracker.shared.pubsub.DomainEvent;

public class FuelCutRequestedEvent extends DomainEvent {
    private final String vehicleId;
    private final String deviceId;

    public FuelCutRequestedEvent(String vehicleId, String deviceId) {
        this.vehicleId = vehicleId;
        this.deviceId = deviceId;
    }

    public String getVehicleId() { return vehicleId; }
    public String getDeviceId() { return deviceId; }
}
