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
    
    List<Trip> findByVehicleVehicleId(String vehicleId);
    
    Optional<Trip> findByVehicleVehicleIdAndIsActive(String vehicleId, boolean isActive);
    
    List<Trip> findByVehicleVehicleIdAndStartTimeBetween(
        String vehicleId, Instant startTime, Instant endTime);
    
    List<Trip> findByIsActive(boolean isActive);
    
    @Query("SELECT t FROM Trip t WHERE t.vehicle.vehicleId = :vehicleId " +
           "AND t.endReason = :endReason " +
           "AND t.endTime >= :startTime")
    List<Trip> findTripsByEndReason(
        @Param("vehicleId") String vehicleId,
        @Param("endReason") TripEndReason endReason,
        @Param("startTime") Instant startTime);
    
    @Query("SELECT t FROM Trip t WHERE t.vehicle.vehicleId = :vehicleId " +
           "AND t.isActive = false " +
           "AND t.totalDistanceKm > :minDistance " +
           "ORDER BY t.endTime DESC")
    Page<Trip> findRecentCompletedTrips(
        @Param("vehicleId") String vehicleId,
        @Param("minDistance") float minDistance,
        Pageable pageable);
    
    @Query("SELECT SUM(t.totalDistanceKm) FROM Trip t " +
           "WHERE t.vehicle.vehicleId = :vehicleId " +
           "AND t.isActive = false " +
           "AND t.endTime BETWEEN :startTime AND :endTime")
    Float getTotalDistanceForPeriod(
        @Param("vehicleId") String vehicleId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime);
}