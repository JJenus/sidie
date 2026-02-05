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
    
    Page<TrackerAlert> findByVehicleId(String vehicleId, Pageable pageable);
    
    Page<TrackerAlert> findByTrackerId(String trackerId, Pageable pageable);
    
    List<TrackerAlert> findByAlertTypeAndTriggeredAtAfter(
        String alertType, Instant triggeredAfter);
    
    List<TrackerAlert> findBySeverityAndAcknowledged(
            AlertSeverity severity, boolean acknowledged);
    
    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.vehicleId = :vehicleId " +
           "AND ta.triggeredAt >= :startTime AND ta.triggeredAt <= :endTime " +
           "ORDER BY ta.triggeredAt DESC")
    List<TrackerAlert> findVehicleAlertsInRange(
        @Param("vehicleId") String vehicleId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime);
    
    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.acknowledged = false " +
           "AND ta.resolved = false " +
           "ORDER BY ta.severity DESC, ta.triggeredAt DESC")
    Page<TrackerAlert> findActiveAlerts(Pageable pageable);
    
    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.vehicleId = :vehicleId " +
           "AND ta.acknowledged = false " +
           "AND ta.resolved = false " +
           "ORDER BY ta.triggeredAt DESC")
    List<TrackerAlert> findActiveVehicleAlerts(@Param("vehicleId") String vehicleId);
    
    @Query("SELECT COUNT(ta) FROM TrackerAlert ta WHERE ta.acknowledged = false " +
           "AND ta.severity = :severity")
    Long countUnacknowledgedBySeverity(@Param("severity") AlertSeverity severity);
    
    @Query("SELECT ta.alertType, COUNT(ta) FROM TrackerAlert ta " +
           "WHERE ta.triggeredAt >= :startTime " +
           "GROUP BY ta.alertType " +
           "ORDER BY COUNT(ta) DESC")
    List<Object[]> getAlertTypeStatistics(@Param("startTime") Instant startTime);
    
    @Query("SELECT ta FROM TrackerAlert ta WHERE ta.resolved = false " +
           "AND ta.triggeredAt < :cutoffTime")
    List<TrackerAlert> findStaleAlerts(@Param("cutoffTime") Instant cutoffTime);
}