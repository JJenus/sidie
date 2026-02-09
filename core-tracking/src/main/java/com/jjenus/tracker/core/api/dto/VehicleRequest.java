// === VehicleRequest.java ===
package com.jjenus.tracker.core.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Range;

public class VehicleRequest {
    @NotBlank(message = "Vehicle ID is required")
    @Size(min = 3, max = 50, message = "Vehicle ID must be 3-50 characters")
    private String vehicleId;

    @Size(max = 50, message = "Device ID must be max 50 characters")
    private String deviceId;

    @Size(max = 100, message = "Model must be max 100 characters")
    private String model;

    @Size(max = 20, message = "License plate must be max 20 characters")
    private String licensePlate;

    @Size(max = 17, message = "VIN must be max 17 characters")
    private String vin;

    private Float fuelLevel;

    @Range(min = 0, max = 999999, message = "Odometer must be between 0-999,999")
    private Float odometerKm;

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
}

