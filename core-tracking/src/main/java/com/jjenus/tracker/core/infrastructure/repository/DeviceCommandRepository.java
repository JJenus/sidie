package com.jjenus.tracker.core.infrastructure.repository;

import com.jjenus.tracker.core.domain.enums.CommandStatus;
import com.jjenus.tracker.core.domain.entity.DeviceCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, Long> {

    Page<DeviceCommand> findByTrackerTrackerId(String trackerId, Pageable pageable);

    List<DeviceCommand> findByStatus(CommandStatus status);

    List<DeviceCommand> findByTrackerTrackerIdAndStatus(String trackerId, CommandStatus status);

    @Query("SELECT dc FROM DeviceCommand dc WHERE dc.status IN ('PENDING', 'FAILED', 'TIMEOUT') " +
            "AND dc.retryCount < dc.maxRetries " +
            "AND dc.createdAt >= :cutoffTime " +
            "ORDER BY dc.createdAt")
    List<DeviceCommand> findPendingAndRetryableCommands(@Param("cutoffTime") Instant cutoffTime);

    @Query("SELECT dc FROM DeviceCommand dc WHERE dc.tracker.deviceId = :deviceId " +
            "AND dc.status IN ('SENT', 'DELIVERED') " +
            "ORDER BY dc.sentAt DESC LIMIT 10")
    List<DeviceCommand> findRecentCommandsByDeviceId(@Param("deviceId") String deviceId);

    @Query("SELECT COUNT(dc) FROM DeviceCommand dc WHERE dc.status = 'PENDING' " +
            "AND dc.tracker.trackerId = :trackerId")
    Long countPendingCommands(@Param("trackerId") String trackerId);

    @Transactional
    @Modifying
    @Query("UPDATE DeviceCommand dc SET dc.status = 'CANCELLED', dc.updatedAt = :now " +
            "WHERE dc.commandId = :commandId AND dc.status = 'PENDING'")
    int cancelPendingCommand(@Param("commandId") Long commandId, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("DELETE FROM DeviceCommand dc WHERE dc.createdAt < :cutoffTime " +
            "AND dc.status IN ('DELIVERED', 'CANCELLED', 'FAILED')")
    int cleanupOldCommands(@Param("cutoffTime") Instant cutoffTime);
}
