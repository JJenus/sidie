package com.jjenus.tracker.core.infrastructure.repository;

import com.jjenus.tracker.core.domain.entity.Geofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeofenceRepository extends JpaRepository<Geofence, Long> {
    
    List<Geofence> findByVehicleVehicleId(String vehicleId);
    
    List<Geofence> findByVehicleVehicleIdAndIsActive(String vehicleId, boolean isActive);
    
    @Query("SELECT g FROM Geofence g WHERE g.isActive = true " +
           "AND (:latitude BETWEEN g.centerLatitude - (g.radiusMeters / 111000.0) " +
           "AND g.centerLatitude + (g.radiusMeters / 111000.0)) " +
           "AND (:longitude BETWEEN g.centerLongitude - (g.radiusMeters / (111000.0 * COS(RADIANS(g.centerLatitude)))) " +
           "AND g.centerLongitude + (g.radiusMeters / (111000.0 * COS(RADIANS(g.centerLatitude)))))")
    List<Geofence> findNearbyGeofences(
        @Param("latitude") Double latitude,
        @Param("longitude") Double longitude);
}