package com.jjenus.tracker.core.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Range;

@Data
public class VehicleUpdateRequest {
    @Size(max = 100, message = "Model must be max 100 characters")
    private String model;

    @Size(max = 20, message = "License plate must be max 20 characters")
    private String licensePlate;

    @Size(max = 17, message = "VIN must be max 17 characters")
    private String vin;

    @Range(min = 0, max = 100, message = "Fuel level must be 0-100%")
    private Float fuelLevel;

    @Range(min = 0, max = 999999, message = "Odometer must be between 0-999,999")
    private Float odometerKm;

    private Boolean fuelCutActive;
}
