package com.jjenus.tracker.core.infrastructure.repository;

import com.jjenus.tracker.core.domain.entity.Trip;
import com.jjenus.tracker.core.domain.enums.TripEndReason;
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
public interface TripRepository extends JpaRepository<Trip, String> {
    
    List<Trip> findByVehicleVehicleIdAndOrganizationId(String vehicleId, Long organizationId);
    
    Optional<Trip> findByVehicleVehicleIdAndIsActiveAndOrganizationId(String vehicleId, boolean isActive, Long organizationId);
    
    List<Trip> findByVehicleVehicleIdAndStartTimeBetweenAndOrganizationId(
        String vehicleId, Instant startTime, Instant endTime, Long organizationId);
    
    List<Trip> findByIsActiveAndOrganizationId(boolean isActive, Long organizationId);
    
    @Query("SELECT t FROM Trip t WHERE t.vehicle.vehicleId = :vehicleId " +
           "AND t.endReason = :endReason " +
           "AND t.endTime >= :startTime " +
           "AND t.vehicle.organizationId = :organizationId")
    List<Trip> findTripsByEndReason(
        @Param("vehicleId") String vehicleId,
        @Param("endReason") TripEndReason endReason,
        @Param("startTime") Instant startTime,
        @Param("organizationId") Long organizationId);
    
    @Query("SELECT t FROM Trip t WHERE t.vehicle.vehicleId = :vehicleId " +
           "AND t.isActive = false " +
           "AND t.totalDistanceKm > :minDistance " +
           "AND t.vehicle.organizationId = :organizationId " +
           "ORDER BY t.endTime DESC")
    Page<Trip> findRecentCompletedTrips(
        @Param("vehicleId") String vehicleId,
        @Param("minDistance") float minDistance,
        @Param("organizationId") Long organizationId,
        Pageable pageable);
    
    @Query("SELECT SUM(t.totalDistanceKm) FROM Trip t " +
           "WHERE t.vehicle.vehicleId = :vehicleId " +
           "AND t.isActive = false " +
           "AND t.endTime BETWEEN :startTime AND :endTime " +
           "AND t.vehicle.organizationId = :organizationId")
    Float getTotalDistanceForPeriod(
        @Param("vehicleId") String vehicleId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        @Param("organizationId") Long organizationId);

    Page<Trip> findByOrganizationId(Long organizationId, Pageable pageable);
}
