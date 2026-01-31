package com.jjenus.tracker.notification.infrastructure.repository;

import com.jjenus.tracker.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    Optional<Notification> findByNotificationId(String notificationId);
    
    Page<Notification> findByRecipient(String recipient, Pageable pageable);
    
    @Query("SELECT n FROM Notification n WHERE n.recipient = :recipient AND n.readAt IS NULL")
    Page<Notification> findUnreadByUserId(@Param("recipient") String recipient, Pageable pageable);
    
    @Query("SELECT n FROM Notification n WHERE " +
           "(:userId IS NULL OR n.recipient = :userId) AND " +
           "(:status IS NULL OR n.status = :status) AND " +
           "(:channel IS NULL OR n.channel = :channel) AND " +
           "(:alertId IS NULL OR n.alertId = :alertId) " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findWithFilters(
        @Param("userId") String userId,
        @Param("status") String status,
        @Param("channel") String channel,
        @Param("alertId") String alertId,
        Pageable pageable
    );
    
    @Query("SELECT n FROM Notification n WHERE n.recipient = :userId AND n.readAt IS NULL")
    List<Notification> findUnreadByUserId(@Param("userId") String userId);
    
    void deleteByNotificationId(String notificationId);
}
