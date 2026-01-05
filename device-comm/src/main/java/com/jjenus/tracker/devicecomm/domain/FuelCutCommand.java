package com.jjenus.tracker.devicecomm.domain;

import com.jjenus.tracker.shared.pubsub.DomainEvent;

public class FuelCutCommand extends DomainEvent {
    private final String vehicleId;
    private final String deviceId;
    
    public FuelCutCommand(String vehicleId, String deviceId) {
        this.vehicleId = vehicleId;
        this.deviceId = deviceId;
    }
    
    public String getVehicleId() { return vehicleId; }
    public String getDeviceId() { return deviceId; }
}
