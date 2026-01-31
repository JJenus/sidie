package com.jjenus.tracker.notification.infrastructure.repository;

import com.jjenus.tracker.notification.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    
    List<NotificationPreference> findByUserId(String userId);
    
    Optional<NotificationPreference> findByUserIdAndAlertType(String userId, String alertType);
    
    List<NotificationPreference> findByUserIdAndAlertTypeIn(String userId, List<String> alertTypes);
    
    void deleteByUserId(String userId);
    
    void deleteByUserIdAndAlertType(String userId, String alertType);
}
