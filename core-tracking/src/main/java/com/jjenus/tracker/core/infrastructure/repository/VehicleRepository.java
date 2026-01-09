package com.jjenus.tracker.core.infrastructure.repository;

import com.jjenus.tracker.core.domain.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, String> {
    
    Optional<Vehicle> findByDeviceId(String deviceId);
    
    Optional<Vehicle> findByLicensePlate(String licensePlate);
    
    List<Vehicle> findByEngineState(String engineState);
    
    @Query("SELECT v FROM Vehicle v WHERE v.lastTelemetryTime < :cutoffTime AND v.accStatus = true")
    List<Vehicle> findVehiclesWithStaleTelemetry(@Param("cutoffTime") Instant cutoffTime);
    
    @Query("SELECT v FROM Vehicle v WHERE EXISTS (SELECT t FROM v.trips t WHERE t.isActive = true)")
    List<Vehicle> findVehiclesWithActiveTrips();
    
    @Query("SELECT v FROM Vehicle v WHERE v.fuelCutActive = true")
    List<Vehicle> findVehiclesWithActiveFuelCut();
    
    boolean existsByDeviceId(String deviceId);
    
    boolean existsByLicensePlate(String licensePlate);
}