package com.jjenus.tracker.alerting.infrastructure.repository;

import com.jjenus.tracker.alerting.domain.entity.TrackerAlert;
import com.jjenus.tracker.alerting.domain.enums.AlertSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TrackerAlertRepository extends JpaRepository<TrackerAlert, Long> {

    // Basic queries
    Page<TrackerAlert> findByVehicleId(String vehicleId, Pageable pageable);

    Page<TrackerAlert> findByTrackerId(String trackerId, Pageable pageable);

    List<TrackerAlert> findByAlertTypeAndTriggeredAtAfter(
        String alertType, Instant triggeredAfter);

    List<TrackerAlert> findBySeverityAndAcknowledged(
            AlertSeverity severity, boolean acknowledged);

    // Advanced queries with date ranges
    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.vehicleId = :vehicleId " +
           "AND ta.triggeredAt >= :startTime AND ta.triggeredAt <= :endTime " +
           "ORDER BY ta.triggeredAt DESC")
    List<TrackerAlert> findVehicleAlertsInRange(
        @Param("vehicleId") String vehicleId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime);

    // Active alerts (not acknowledged and not resolved)
    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.acknowledged = false " +
           "AND ta.resolved = false " +
           "ORDER BY ta.severity DESC, ta.triggeredAt DESC")
    Page<TrackerAlert> findActiveAlerts(Pageable pageable);

    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.vehicleId = :vehicleId " +
           "AND ta.acknowledged = false " +
           "AND ta.resolved = false " +
           "ORDER BY ta.triggeredAt DESC")
    List<TrackerAlert> findActiveVehicleAlerts(@Param("vehicleId") String vehicleId);

    // Statistics queries
    @Query("SELECT COUNT(ta) FROM TrackerAlert ta WHERE ta.acknowledged = false " +
           "AND ta.severity = :severity")
    Long countUnacknowledgedBySeverity(@Param("severity") AlertSeverity severity);

    @Query("SELECT ta.alertType, COUNT(ta) FROM TrackerAlert ta " +
           "WHERE ta.triggeredAt >= :startTime " +
           "GROUP BY ta.alertType " +
           "ORDER BY COUNT(ta) DESC")
    List<Object[]> getAlertTypeStatistics(@Param("startTime") Instant startTime);

    // Stale alerts (unresolved older than cutoff)
    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.resolved = false " +
           "AND ta.triggeredAt < :cutoffTime")
    List<TrackerAlert> findStaleAlerts(@Param("cutoffTime") Instant cutoffTime);

    // Comprehensive search query
    @Query("SELECT ta FROM TrackerAlert ta WHERE " +
           "(:vehicleId IS NULL OR ta.vehicleId = :vehicleId) " +
           "AND (:trackerId IS NULL OR ta.trackerId = :trackerId) " +
           "AND (:alertType IS NULL OR ta.alertType = :alertType) " +
           "AND (:severity IS NULL OR ta.severity = :severity) " +
           "AND (:acknowledged IS NULL OR ta.acknowledged = :acknowledged) " +
           "AND (:resolved IS NULL OR ta.resolved = :resolved) " +
           "AND (:startDate IS NULL OR ta.triggeredAt >= :startDate) " +
           "AND (:endDate IS NULL OR ta.triggeredAt <= :endDate) " +
           "AND (:search IS NULL OR LOWER(ta.message) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY " +
           "CASE WHEN :sortBy = 'severity' THEN ta.severity END DESC, " +
           "CASE WHEN :sortBy = 'triggeredAt' THEN ta.triggeredAt END DESC, " +
           "CASE WHEN :sortBy = 'acknowledgedAt' THEN ta.acknowledgedAt END DESC, " +
           "ta.triggeredAt DESC")
    Page<TrackerAlert> searchAlerts(
            @Param("vehicleId") String vehicleId,
            @Param("trackerId") String trackerId,
            @Param("alertType") String alertType,
            @Param("severity") AlertSeverity severity,
            @Param("acknowledged") Boolean acknowledged,
            @Param("resolved") Boolean resolved,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            @Param("search") String search,
            Pageable pageable);

    // Batch operations
    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.alertId IN :alertIds")
    List<TrackerAlert> findByIds(@Param("alertIds") List<Long> alertIds);

    // Dashboard statistics
    @Query("SELECT COUNT(ta) FROM TrackerAlert ta WHERE " +
           "ta.triggeredAt >= :startTime AND ta.triggeredAt <= :endTime " +
           "AND ta.severity = :severity")
    Long countBySeverityAndTimeRange(
            @Param("severity") AlertSeverity severity,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);

    @Query("SELECT ta.vehicleId, COUNT(ta) FROM TrackerAlert ta " +
           "WHERE ta.triggeredAt >= :startTime " +
           "GROUP BY ta.vehicleId " +
           "ORDER BY COUNT(ta) DESC")
    List<Object[]> getAlertCountByVehicle(@Param("startTime") Instant startTime);

    // Resolution time statistics
    @Query("SELECT AVG(TIMESTAMPDIFF(SECOND, ta.triggeredAt, ta.resolvedAt)) " +
           "FROM TrackerAlert ta WHERE ta.resolved = true " +
           "AND ta.resolvedAt IS NOT NULL " +
           "AND ta.triggeredAt >= :startTime")
    Double getAverageResolutionTime(@Param("startTime") Instant startTime);
}
