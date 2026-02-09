package com.jjenus.tracker.core.infrastructure.repository;

import com.jjenus.tracker.core.domain.enums.CommandStatus;
import com.jjenus.tracker.core.domain.entity.TrackerCommand;
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
public interface TrackerCommandRepository extends JpaRepository<TrackerCommand, Long> {

    Page<TrackerCommand> findByTrackerTrackerId(String trackerId, Pageable pageable);

    List<TrackerCommand> findByStatus(CommandStatus status);

    List<TrackerCommand> findByTrackerTrackerIdAndStatus(String trackerId, CommandStatus status);

    @Query("SELECT dc FROM TrackerCommand dc WHERE dc.status IN ('PENDING', 'FAILED', 'TIMEOUT') " +
            "AND dc.retryCount < dc.maxRetries " +
            "AND dc.createdAt >= :cutoffTime " +
            "ORDER BY dc.createdAt")
    List<TrackerCommand> findPendingAndRetryableCommands(@Param("cutoffTime") Instant cutoffTime);

    @Query("SELECT dc FROM TrackerCommand dc WHERE dc.tracker.trackerId = :trackerId " +
            "AND dc.status IN ('SENT', 'DELIVERED') " +
            "ORDER BY dc.sentAt DESC LIMIT 10")
    List<TrackerCommand> findRecentCommandsByTrackerId(@Param("trackerId") String trackerId);

    @Query("SELECT COUNT(dc) FROM TrackerCommand dc WHERE dc.status = 'PENDING' " +
            "AND dc.tracker.trackerId = :trackerId")
    Long countPendingCommands(@Param("trackerId") String trackerId);

    @Transactional
    @Modifying
    @Query("UPDATE TrackerCommand dc SET dc.status = 'CANCELLED', dc.updatedAt = :now " +
            "WHERE dc.commandId = :commandId AND dc.status = 'PENDING'")
    int cancelPendingCommand(@Param("commandId") Long commandId, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("DELETE FROM TrackerCommand dc WHERE dc.createdAt < :cutoffTime " +
            "AND dc.status IN ('DELIVERED', 'CANCELLED', 'FAILED')")
    int cleanupOldCommands(@Param("cutoffTime") Instant cutoffTime);
}
