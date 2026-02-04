package com.jjenus.tracker.alerting.infrastructure.repository;

import com.jjenus.tracker.alerting.domain.entity.Geofence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface GeofenceRepository extends JpaRepository<Geofence, Long> {

    // Find all geofences that include a specific vehicle
    @Query("SELECT g FROM Geofence g WHERE :vehicleId MEMBER OF g.vehicleIds")
    List<Geofence> findByVehicleId(@Param("vehicleId") String vehicleId);

    @Query("SELECT g FROM Geofence g WHERE :vehicleId MEMBER OF g.vehicleIds")
    Page<Geofence> findByVehicleId(@Param("vehicleId") String vehicleId, Pageable pageable);

    // Find active geofences for a specific vehicle
    @Query("SELECT g FROM Geofence g WHERE g.isActive = true AND :vehicleId MEMBER OF g.vehicleIds")
    List<Geofence> findByVehicleIdAndIsActive(@Param("vehicleId") String vehicleId);

    @Query("SELECT g FROM Geofence g WHERE g.isActive = true AND :vehicleId MEMBER OF g.vehicleIds")
    Page<Geofence> findByVehicleIdAndIsActive(@Param("vehicleId") String vehicleId, Pageable pageable);

    // Find geofences by multiple vehicles
    @Query("SELECT DISTINCT g FROM Geofence g JOIN g.vehicleIds vid WHERE vid IN :vehicleIds AND g.isActive = true")
    List<Geofence> findByVehicleIds(@Param("vehicleIds") Set<String> vehicleIds);

    @Query("SELECT DISTINCT g FROM Geofence g JOIN g.vehicleIds vid WHERE vid IN :vehicleIds AND g.isActive = true")
    Page<Geofence> findByVehicleIds(@Param("vehicleIds") Set<String> vehicleIds, Pageable pageable);

    // Find nearby geofences (spatial query)
    @Query("SELECT g FROM Geofence g WHERE g.isActive = true " +
            "AND (:latitude BETWEEN g.centerLatitude - (g.radiusMeters / 111000.0) " +
            "AND g.centerLatitude + (g.radiusMeters / 111000.0)) " +
            "AND (:longitude BETWEEN g.centerLongitude - (g.radiusMeters / (111000.0 * COS(RADIANS(g.centerLatitude)))) " +
            "AND g.centerLongitude + (g.radiusMeters / (111000.0 * COS(RADIANS(g.centerLatitude)))))")
    List<Geofence> findNearbyGeofences(
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude);

    // Find nearby geofences for specific vehicles
    @Query("SELECT g FROM Geofence g WHERE g.isActive = true " +
            "AND (:vehicleId MEMBER OF g.vehicleIds) " +
            "AND (:latitude BETWEEN g.centerLatitude - (g.radiusMeters / 111000.0) " +
            "AND g.centerLatitude + (g.radiusMeters / 111000.0)) " +
            "AND (:longitude BETWEEN g.centerLongitude - (g.radiusMeters / (111000.0 * COS(RADIANS(g.centerLatitude)))) " +
            "AND g.centerLongitude + (g.radiusMeters / (111000.0 * COS(RADIANS(g.centerLatitude)))))")
    List<Geofence> findNearbyGeofencesForVehicle(
            @Param("vehicleId") String vehicleId,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude);

    // Find all vehicles that have geofences
    @Query("SELECT DISTINCT vid FROM Geofence g JOIN g.vehicleIds vid WHERE g.isActive = true")
    Set<String> findVehiclesWithGeofences();

    // Check if vehicle has any geofences
    @Query("SELECT COUNT(g) > 0 FROM Geofence g WHERE :vehicleId MEMBER OF g.vehicleIds AND g.isActive = true")
    boolean hasActiveGeofencesForVehicle(@Param("vehicleId") String vehicleId);

    // Search methods with pagination
    @Query("SELECT g FROM Geofence g WHERE " +
            "(:vehicleId IS NULL OR :vehicleId MEMBER OF g.vehicleIds) " +
            "AND (:search IS NULL OR LOWER(g.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:active IS NULL OR g.isActive = :active)")
    Page<Geofence> searchGeofences(
            @Param("vehicleId") String vehicleId,
            @Param("search") String search,
            @Param("active") Boolean active,
            Pageable pageable);
}