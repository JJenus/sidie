package com.jjenus.tracker.notification.infrastructure.repository;

import com.jjenus.tracker.notification.domain.enums.NotificationChannel;
import com.jjenus.tracker.notification.domain.entity.NotificationTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    
    Optional<NotificationTemplate> findByTemplateId(String templateId);
    
    List<NotificationTemplate> findByTemplateTypeAndChannelAndEnabledTrue(String templateType, NotificationChannel channel);
    
    List<NotificationTemplate> findByChannelAndEnabledTrue(NotificationChannel channel);
    
    @Query("SELECT t FROM NotificationTemplate t WHERE " +
           "(:templateType IS NULL OR t.templateType = :templateType) AND " +
           "(:channel IS NULL OR t.channel = :channel) AND " +
           "t.enabled = true " +
           "ORDER BY t.createdAt DESC")
    Page<NotificationTemplate> findWithFilters(
        @Param("templateType") String templateType,
        @Param("channel") String channel,
        Pageable pageable
    );
    
    boolean existsByTemplateId(String templateId);
    
    void deleteByTemplateId(String templateId);
}
