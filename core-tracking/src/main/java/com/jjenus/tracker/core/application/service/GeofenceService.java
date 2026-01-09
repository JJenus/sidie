package com.jjenus.tracker.core.application.service;

import com.jjenus.tracker.core.domain.entity.*;
import com.jjenus.tracker.core.infrastructure.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GeofenceService {
    
    private final GeofenceRepository geofenceRepository;
    private final VehicleRepository vehicleRepository;
    
    public GeofenceService(GeofenceRepository geofenceRepository,
                          VehicleRepository vehicleRepository) {
        this.geofenceRepository = geofenceRepository;
        this.vehicleRepository = vehicleRepository;
    }
    
    @Transactional
    public Geofence createGeofence(String vehicleId, Geofence geofence) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
        
        geofence.setVehicle(vehicle);
        return geofenceRepository.save(geofence);
    }
    
    @Transactional(readOnly = true)
    public List<Geofence> getVehicleGeofences(String vehicleId) {
        return geofenceRepository.findByVehicleVehicleId(vehicleId);
    }
    
    @Transactional(readOnly = true)
    public List<Geofence> getActiveGeofences(String vehicleId) {
        return geofenceRepository.findByVehicleVehicleIdAndIsActive(vehicleId, true);
    }
    
    @Transactional
    public void checkGeofenceViolations(String vehicleId, Double latitude, Double longitude) {
        List<Geofence> geofences = geofenceRepository.findNearbyGeofences(latitude, longitude);
        
        for (Geofence geofence : geofences) {
            if (geofence.getVehicle().getVehicleId().equals(vehicleId)) {
                boolean isInside = geofence.isPointInside(latitude, longitude);
                
                // Check for violations (exit/entry)
                // This would trigger alerts
                System.out.println("Geofence check for " + geofence.getName() + 
                                 ": Vehicle is " + (isInside ? "inside" : "outside"));
            }
        }
    }
    
    @Transactional
    public Geofence updateGeofence(Long geofenceId, Geofence updates) {
        Geofence geofence = geofenceRepository.findById(geofenceId)
            .orElseThrow(() -> new IllegalArgumentException("Geofence not found"));
        
        if (updates.getName() != null) geofence.setName(updates.getName());
        if (updates.getIsActive() != null) geofence.setIsActive(updates.getIsActive());
        if (updates.getShapeType() != null) geofence.setShapeType(updates.getShapeType());
        
        return geofenceRepository.save(geofence);
    }
    
    @Transactional
    public void deleteGeofence(Long geofenceId) {
        geofenceRepository.deleteById(geofenceId);
    }
}