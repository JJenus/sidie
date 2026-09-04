package com.jjenus.tracker.core.infrastructure.repository;

import com.jjenus.tracker.core.domain.entity.Vehicle;
import com.jjenus.tracker.core.domain.enums.EngineState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, String> {
    
    Optional<Vehicle> findByDeviceIdAndOrganizationId(String deviceId, Long organizationId);
    
    Optional<Vehicle> findByIdAndOrganizationId(String vehicleId, Long organizationId);
    
    long deleteByIdAndOrganizationId(String vehicleId, Long organizationId);
    
    Optional<Vehicle> findByLicensePlateAndOrganizationId(String licensePlate, Long organizationId);
    
    @Query("SELECT v FROM Vehicle v WHERE v.engineState = :engineState AND v.organizationId = :organizationId")
    Page<Vehicle> findByEngineStateAndOrganizationId(@Param("engineState") EngineState engineState, @Param("organizationId") Long organizationId, Pageable pageable);
    
    @Query("SELECT v FROM Vehicle v WHERE v.lastTelemetryTime < :cutoffTime AND v.accStatus = true AND v.organizationId = :organizationId")
    Page<Vehicle> findVehiclesWithStaleTelemetry(@Param("cutoffTime") Instant cutoffTime, @Param("organizationId") Long organizationId, Pageable pageable);
    
    @Query("SELECT v FROM Vehicle v WHERE EXISTS (SELECT t FROM v.trips t WHERE t.isActive = true) AND v.organizationId = :organizationId")
    Page<Vehicle> findVehiclesWithActiveTrips(@Param("organizationId") Long organizationId, Pageable pageable);
    
    @Query("SELECT v FROM Vehicle v WHERE v.fuelCutActive = true AND v.organizationId = :organizationId")
    Page<Vehicle> findVehiclesWithActiveFuelCut(@Param("organizationId") Long organizationId, Pageable pageable);
    
    boolean existsByDeviceIdAndOrganizationId(String deviceId, Long organizationId);
    
    boolean existsByLicensePlateAndOrganizationId(String licensePlate, Long organizationId);

    Page<Vehicle> findByOrganizationId(Long organizationId, Pageable pageable);
}
