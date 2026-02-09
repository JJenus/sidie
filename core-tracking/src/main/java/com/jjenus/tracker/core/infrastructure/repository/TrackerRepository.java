package com.jjenus.tracker.core.infrastructure.repository;

import com.jjenus.tracker.core.domain.entity.Tracker;
import com.jjenus.tracker.core.domain.enums.TrackerStatus;
import jakarta.validation.constraints.Size;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrackerRepository extends JpaRepository<Tracker, String> {
    
    Optional<Tracker> findByTrackerId(String deviceId);

    List<Tracker> findByIsOnline(boolean isOnline);
    
    List<Tracker> findByStatus(TrackerStatus status);
    
    @Query("SELECT t FROM Tracker t WHERE t.lastSeen < :cutoffTime AND t.isOnline = true")
    List<Tracker> findStaleConnections(@Param("cutoffTime") Instant cutoffTime);
    
    @Query("SELECT t FROM Tracker t WHERE t.batteryLevel < :threshold")
    List<Tracker> findTrackersWithLowBattery(@Param("threshold") float threshold);

    boolean existsBySimNumber(String simNumber);
}