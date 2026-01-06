package com.jjenus.tracker.core.domain;

import com.jjenus.tracker.core.exception.TripException;
import com.jjenus.tracker.core.exception.VehicleException;
import com.jjenus.tracker.shared.domain.LocationPoint;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Vehicle {
    private final String vehicleId;
    private String deviceId;
    private LocationPoint currentLocation;
    private EngineState engineState;
    private FuelStatus fuelStatus;
    private boolean isFuelCutActive;
    private Instant lastMovementTime;
    private Trip activeTrip;
    private final List<String> recentAlerts;

    public Vehicle(String vehicleId) {
        this.vehicleId = vehicleId;
        this.engineState = EngineState.OFF;
        this.isFuelCutActive = false;
        this.currentLocation = new LocationPoint(0.0, 0.0, 0.0f, Instant.now());
        this.fuelStatus = new FuelStatus(100.0f, 0.0f, Instant.now());
        this.recentAlerts = new ArrayList<>();
    }

    public void processNewTelemetry(LocationPoint newLocation) {
        if (!newLocation.isValid()) {
            throw VehicleException.invalidLocationData();
        }

        this.currentLocation = newLocation;

        if (newLocation.speedKmh() > 0) {
            this.engineState = EngineState.MOVING;
            this.lastMovementTime = Instant.now();

            if (this.activeTrip == null) {
                startTrip(newLocation);
            }
        } else if (this.engineState == EngineState.MOVING) {
            this.engineState = EngineState.IDLE;
            this.lastMovementTime = Instant.now();
        }

        if (this.engineState == EngineState.MOVING && this.activeTrip != null) {
            this.activeTrip.addLocation(newLocation);
        }
    }

    private void startTrip(LocationPoint startLocation) {
        if (this.activeTrip != null) {
            throw com.jjenus.tracker.core.exception.TripException.alreadyActive(vehicleId);
        }
        this.activeTrip = new Trip(this.vehicleId, startLocation);
        System.out.println("DOMAIN EVENT: Trip started for vehicle " + vehicleId);
    }

    public void endTrip(LocationPoint endLocation) {
        if (this.activeTrip == null) {
            throw TripException.notActive(vehicleId);
        }
        this.activeTrip.end(endLocation);
        System.out.println("DOMAIN EVENT: Trip ended for vehicle " + vehicleId);
        this.activeTrip = null;
    }

    public void issueFuelCutOffCommand() {
        if (this.currentLocation.speedKmh() > 10) {
            throw VehicleException.fuelCutNotAllowedWhileMoving(this.currentLocation.speedKmh());
        }

        if (this.isFuelCutActive) {
            throw VehicleException.fuelCutAlreadyActive();
        }

        this.isFuelCutActive = true;
        this.engineState = EngineState.FUEL_CUT_ACTIVE;

        System.out.println("DOMAIN EVENT: Fuel cut requested for vehicle " + vehicleId);
    }

    public void issueFuelRestoreCommand() {
        this.isFuelCutActive = false;

        if (this.currentLocation.speedKmh() > 0) {
            this.engineState = EngineState.MOVING;
        } else {
            this.engineState = EngineState.ON;
        }

        System.out.println("DOMAIN EVENT: Fuel restore requested for vehicle " + vehicleId);
    }

    public Duration getIdleDuration() {
        if (this.lastMovementTime == null || this.engineState != EngineState.IDLE) {
            return Duration.ZERO;
        }
        return Duration.between(this.lastMovementTime, Instant.now());
    }

    public void addAlert(String alertMessage) {
        this.recentAlerts.add(Instant.now() + ": " + alertMessage);
        if (this.recentAlerts.size() > 100) {
            this.recentAlerts.remove(0);
        }
    }

    public String getVehicleId() { return vehicleId; }
    public String getDeviceId() { return deviceId; }
    public LocationPoint getCurrentLocation() { return currentLocation; }
    public EngineState getEngineState() { return engineState; }
    public FuelStatus getFuelStatus() { return fuelStatus; }
    public boolean isFuelCutActive() { return isFuelCutActive; }
    public Trip getActiveTrip() { return activeTrip; }
    public List<String> getRecentAlerts() { return new ArrayList<>(recentAlerts); }

    protected void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    protected void setFuelStatus(FuelStatus fuelStatus) { this.fuelStatus = fuelStatus; }
}
