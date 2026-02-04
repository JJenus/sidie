package com.jjenus.tracker.notification.application;

import com.jjenus.tracker.notification.api.dto.*;
import com.jjenus.tracker.notification.domain.entity.Notification;
import com.jjenus.tracker.notification.domain.entity.NotificationPreference;
import com.jjenus.tracker.notification.domain.entity.NotificationTemplate;
import com.jjenus.tracker.notification.infrastructure.repository.NotificationRepository;
import com.jjenus.tracker.notification.infrastructure.repository.NotificationPreferenceRepository;
import com.jjenus.tracker.notification.infrastructure.repository.NotificationTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class NotificationQueryService {
    
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationTemplateRepository templateRepository;
    
    public NotificationQueryService(
        NotificationRepository notificationRepository,
        NotificationPreferenceRepository preferenceRepository,
        NotificationTemplateRepository templateRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.templateRepository = templateRepository;
    }
    
    public Page<NotificationResponse> findNotifications(
        String userId, 
        String status, 
        String channel, 
        String alertId,
        Pageable pageable
    ) {
        Page<Notification> notifications = notificationRepository.findWithFilters(
            userId, status, channel, alertId, pageable
        );
        return notifications.map(this::toResponse);
    }
    
    public NotificationResponse getNotificationById(String notificationId) {
        Notification notification = notificationRepository.findByNotificationId(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        return toResponse(notification);
    }
    
    public Page<NotificationResponse> getUserNotifications(
        String userId, 
        boolean unreadOnly, 
        Pageable pageable
    ) {
        Page<Notification> notifications = unreadOnly
            ? notificationRepository.findUnreadByUserId(userId, pageable)
            : notificationRepository.findByRecipient(userId, pageable);
        return notifications.map(this::toResponse);
    }
    
    public List<NotificationPreferenceResponse> getUserPreferences(String userId) {
        List<NotificationPreference> preferences = 
            preferenceRepository.findByUserId(userId);
        return preferences.stream()
            .map(this::toPreferenceResponse)
            .collect(Collectors.toList());
    }
    
    public Page<NotificationTemplateResponse> getTemplates(
        String templateType, 
        String channel, 
        Pageable pageable
    ) {
        Page<NotificationTemplate> templates = templateRepository.findWithFilters(
            templateType, channel, pageable
        );
        return templates.map(this::toTemplateResponse);
    }
    
    private NotificationResponse toResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setNotificationId(notification.getNotificationId());
        response.setAlertId(notification.getAlertId());
        response.setChannel(notification.getChannel().name());
        response.setRecipient(notification.getRecipient());
        response.setTitle(notification.getTitle());
        response.setMessage(notification.getMessage());
        response.setStatus(notification.getStatus().name());
        response.setSentAt(notification.getSentAt());
        response.setDeliveredAt(notification.getDeliveredAt());
        response.setReadAt(notification.getReadAt());
        response.setCreatedAt(notification.getCreatedAt());
        return response;
    }
    
    private NotificationPreferenceResponse toPreferenceResponse(NotificationPreference preference) {
        NotificationPreferenceResponse response = new NotificationPreferenceResponse();
        response.setUserId(preference.getUserId());
        response.setAlertType(preference.getAlertType());
        response.setEnabled(preference.isEnabled());
        response.setEnabledChannels(preference.getEnabledChannels().stream()
            .map(Enum::name)
            .collect(Collectors.toSet()));
        response.setUpdatedAt(preference.getUpdatedAt());
        return response;
    }
    
    private NotificationTemplateResponse toTemplateResponse(NotificationTemplate template) {
        NotificationTemplateResponse response = new NotificationTemplateResponse();
        response.setTemplateId(template.getTemplateId());
        response.setName(template.getName());
        response.setTemplateType(template.getTemplateType());
        response.setChannel(template.getChannel().name());
        response.setSubjectTemplate(template.getSubjectTemplate());
        response.setBodyTemplate(template.getBodyTemplate());
        response.setLanguage(template.getLanguage());
        response.setEnabled(template.isEnabled());
        response.setCreatedAt(template.getCreatedAt());
        return response;
    }
}
