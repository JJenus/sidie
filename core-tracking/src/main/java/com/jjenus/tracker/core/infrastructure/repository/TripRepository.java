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

    Page<Trip> findByVehicleVehicleId(String vehicleId, Pageable pageable);

    Optional<Trip> findByVehicleVehicleIdAndIsActive(String vehicleId, boolean isActive);

    List<Trip> findByVehicleVehicleIdAndStartTimeBetween(
            String vehicleId, Instant startTime, Instant endTime);

    Page<Trip> findByVehicleVehicleIdAndStartTimeBetween(
            String vehicleId, Instant startTime, Instant endTime, Pageable pageable);

    List<Trip> findByIsActive(boolean isActive);

    Page<Trip> findByIsActive(boolean isActive, Pageable pageable);

    @Query("SELECT t FROM Trip t WHERE t.vehicle.vehicleId = :vehicleId " +
            "AND t.endReason = :endReason " +
            "AND t.endTime >= :startTime")
    List<Trip> findTripsByEndReason(
            @Param("vehicleId") String vehicleId,
            @Param("endReason") TripEndReason endReason,
            @Param("startTime") Instant startTime);

    @Query("SELECT t FROM Trip t WHERE t.vehicle.vehicleId = :vehicleId " +
            "AND t.endReason = :endReason " +
            "AND t.endTime >= :startTime")
    Page<Trip> findTripsByEndReason(
            @Param("vehicleId") String vehicleId,
            @Param("endReason") TripEndReason endReason,
            @Param("startTime") Instant startTime,
            Pageable pageable);

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

    // Optional: Add paginated version of findAll() if not already inherited
    Page<Trip> findAll(Pageable pageable);

    // Optional: Add paginated version with custom query
    @Query("SELECT t FROM Trip t WHERE " +
            "(:vehicleId IS NULL OR t.vehicle.vehicleId = :vehicleId) AND " +
            "(:isActive IS NULL OR t.isActive = :isActive) AND " +
            "(:startTimeFrom IS NULL OR t.startTime >= :startTimeFrom) AND " +
            "(:startTimeTo IS NULL OR t.startTime <= :startTimeTo)")
    Page<Trip> searchTrips(
            @Param("vehicleId") String vehicleId,
            @Param("isActive") Boolean isActive,
            @Param("startTimeFrom") Instant startTimeFrom,
            @Param("startTimeTo") Instant startTimeTo,
            Pageable pageable);
}