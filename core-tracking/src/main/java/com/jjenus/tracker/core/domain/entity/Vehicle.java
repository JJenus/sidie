package com.jjenus.tracker.core.domain.entity;

import com.jjenus.tracker.core.domain.enums.EngineState;
import com.jjenus.tracker.core.domain.enums.TripEndReason;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vehicles")
public class Vehicle {
    
    @Id
    @Column(name = "vehicle_id", length = 50)
    private String vehicleId;
    
    @Column(name = "device_id", length = 50, unique = true)
    private String deviceId;
    
    @Column(name = "model", length = 100)
    private String model;
    
    @Column(name = "license_plate", length = 20)
    private String licensePlate;
    
    @Column(name = "vin", length = 17)
    private String vin;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "engine_state", length = 20)
    private EngineState engineState = EngineState.OFF;
    
    @Column(name = "acc_status")
    private Boolean accStatus = false;
    
    @Column(name = "last_acc_on_time")
    private Instant lastAccOnTime;
    
    @Column(name = "last_acc_off_time")
    private Instant lastAccOffTime;
    
    @Column(name = "last_telemetry_time")
    private Instant lastTelemetryTime;
    
    @Column(name = "fuel_level")
    private Float fuelLevel;
    
    @Column(name = "odometer_km")
    private Float odometerKm;
    
    @Column(name = "is_fuel_cut_active")
    private Boolean fuelCutActive = false;
    
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "current_location_id")
    private TrackerLocation currentLocation;

    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Tracker> trackers = new ArrayList<>();
    
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Trip> trips = new ArrayList<>();
    
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Geofence> geofences = new ArrayList<>();
    
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
    
    // Business methods
    public void updateAccStatus(boolean accOn, Instant timestamp) {
        this.accStatus = accOn;
        
        if (accOn) {
            this.lastAccOnTime = timestamp;
        } else {
            this.lastAccOffTime = timestamp;
        }
        
        this.lastTelemetryTime = timestamp;
    }
    
    public void updateLocation(TrackerLocation location) {
        this.currentLocation = location;
        this.lastTelemetryTime = location.getRecordedAt();
    }
    
    public Trip getActiveTrip() {
        return trips.stream()
            .filter(Trip::getIsActive)
            .findFirst()
            .orElse(null);
    }
    
    public void addTrip(Trip trip) {
        trips.add(trip);
        trip.setVehicle(this);
    }
    
    public void endActiveTrip(TripEndReason reason, TrackerLocation endLocation) {
        Trip activeTrip = getActiveTrip();
        if (activeTrip != null) {
            activeTrip.endTrip(reason, endLocation);
        }
    }
    
    // Getters and Setters
    public String getVehicleId() { return vehicleId; }
    public void setVehicleId(String vehicleId) { this.vehicleId = vehicleId; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public EngineState getEngineState() { return engineState; }
    public void setEngineState(EngineState engineState) { this.engineState = engineState; }
    
    public Boolean getAccStatus() { return accStatus; }
    public void setAccStatus(Boolean accStatus) { this.accStatus = accStatus; }
    
    public Instant getLastAccOnTime() { return lastAccOnTime; }
    public void setLastAccOnTime(Instant lastAccOnTime) { this.lastAccOnTime = lastAccOnTime; }
    
    public Instant getLastAccOffTime() { return lastAccOffTime; }
    public void setLastAccOffTime(Instant lastAccOffTime) { this.lastAccOffTime = lastAccOffTime; }
    
    public Instant getLastTelemetryTime() { return lastTelemetryTime; }
    public void setLastTelemetryTime(Instant lastTelemetryTime) { this.lastTelemetryTime = lastTelemetryTime; }
    
    public TrackerLocation getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(TrackerLocation currentLocation) { this.currentLocation = currentLocation; }
    
    public List<Trip> getTrips() { return trips; }
    public void setTrips(List<Trip> trips) { this.trips = trips; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    // Additional getters/setters
    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }
    
    public String getVin() { return vin; }
    public void setVin(String vin) { this.vin = vin; }
    
    public Float getFuelLevel() { return fuelLevel; }
    public void setFuelLevel(Float fuelLevel) { this.fuelLevel = fuelLevel; }
    
    public Float getOdometerKm() { return odometerKm; }
    public void setOdometerKm(Float odometerKm) { this.odometerKm = odometerKm; }
    
    public Boolean getFuelCutActive() { return fuelCutActive; }
    public void setFuelCutActive(Boolean fuelCutActive) { this.fuelCutActive = fuelCutActive; }
    
    public List<Geofence> getGeofences() { return geofences; }
    public void setGeofences(List<Geofence> geofences) { this.geofences = geofences; }

    public void addTracker(Tracker tracker) {
        trackers.add(tracker);
        tracker.setVehicle(this);
    }

    public void removeTracker(Tracker tracker) {
        trackers.remove(tracker);
        tracker.setVehicle(null);
    }

    public Tracker getActiveTracker() {
        return trackers.stream()
                .filter(t -> Boolean.TRUE.equals(t.getIsOnline()))
                .findFirst()
                .orElse(null);
    }

    // Getters and setters
    public List<Tracker> getTrackers() { return trackers; }
    public void setTrackers(List<Tracker> trackers) { this.trackers = trackers; }
}