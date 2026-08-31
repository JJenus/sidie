package com.jjenus.tracker.core.infrastructure.repository;

import com.jjenus.tracker.core.domain.entity.Tracker;
import com.jjenus.tracker.core.domain.enums.TrackerStatus;
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
public interface TrackerRepository extends JpaRepository<Tracker, String> {
    
    Optional<Tracker> findByDeviceIdAndOrganizationId(String deviceId, Long organizationId);

    @Query("SELECT t FROM Tracker t WHERE t.vehicle.vehicleId = :vehicleId AND t.vehicle.organizationId = :organizationId")
    Optional<Tracker> findByVehicleId(@Param("vehicleId") String vehicleId, @Param("organizationId") Long organizationId);
    
    List<Tracker> findByIsOnlineAndOrganizationId(boolean isOnline, Long organizationId);
    
    List<Tracker> findByStatusAndOrganizationId(TrackerStatus status, Long organizationId);
    
    @Query("SELECT t FROM Tracker t WHERE t.lastSeen < :cutoffTime AND t.isOnline = true AND t.vehicle.organizationId = :organizationId")
    List<Tracker> findStaleConnections(@Param("cutoffTime") Instant cutoffTime, @Param("organizationId") Long organizationId);
    
    @Query("SELECT t FROM Tracker t WHERE t.batteryLevel < :threshold AND t.vehicle.organizationId = :organizationId")
    List<Tracker> findTrackersWithLowBattery(@Param("threshold") float threshold, @Param("organizationId") Long organizationId);
    
    boolean existsByDeviceIdAndOrganizationId(String deviceId, Long organizationId);

    Page<Tracker> findByOrganizationId(Long organizationId, Pageable pageable);

    @Query("SELECT t FROM Tracker t WHERE t.vehicle.organizationId = :organizationId")
    Page<Tracker> findAllByOrganizationId(@Param("organizationId") Long organizationId, Pageable pageable);
}
