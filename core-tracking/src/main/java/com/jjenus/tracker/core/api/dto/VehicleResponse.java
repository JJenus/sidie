package com.jjenus.tracker.core.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjenus.tracker.core.domain.enums.EngineState;

import java.time.Instant;

public class VehicleResponse {
    private String vehicleId;
    private String deviceId;
    private String model;
    private String licensePlate;
    private String vin;
    private EngineState engineState;
    private Boolean accStatus;
    private Float fuelLevel;
    private Float odometerKm;
    private Boolean fuelCutActive;

    private LocationResponse currentLocation;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastAccOnTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastAccOffTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant lastTelemetryTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant updatedAt;

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public EngineState getEngineState() {
        return engineState;
    }

    public void setEngineState(EngineState engineState) {
        this.engineState = engineState;
    }

    public Boolean getAccStatus() {
        return accStatus;
    }

    public void setAccStatus(Boolean accStatus) {
        this.accStatus = accStatus;
    }

    public Float getFuelLevel() {
        return fuelLevel;
    }

    public void setFuelLevel(Float fuelLevel) {
        this.fuelLevel = fuelLevel;
    }

    public Float getOdometerKm() {
        return odometerKm;
    }

    public void setOdometerKm(Float odometerKm) {
        this.odometerKm = odometerKm;
    }

    public Boolean getFuelCutActive() {
        return fuelCutActive;
    }

    public void setFuelCutActive(Boolean fuelCutActive) {
        this.fuelCutActive = fuelCutActive;
    }

    public LocationResponse getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(LocationResponse currentLocation) {
        this.currentLocation = currentLocation;
    }

    public Instant getLastAccOnTime() {
        return lastAccOnTime;
    }

    public void setLastAccOnTime(Instant lastAccOnTime) {
        this.lastAccOnTime = lastAccOnTime;
    }

    public Instant getLastAccOffTime() {
        return lastAccOffTime;
    }

    public void setLastAccOffTime(Instant lastAccOffTime) {
        this.lastAccOffTime = lastAccOffTime;
    }

    public Instant getLastTelemetryTime() {
        return lastTelemetryTime;
    }

    public void setLastTelemetryTime(Instant lastTelemetryTime) {
        this.lastTelemetryTime = lastTelemetryTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
