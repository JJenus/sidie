package com.jjenus.tracker.notification.application;

import com.jjenus.tracker.notification.api.dto.*;
import com.jjenus.tracker.notification.domain.*;
import com.jjenus.tracker.notification.infrastructure.repository.NotificationRepository;
import com.jjenus.tracker.notification.infrastructure.repository.NotificationPreferenceRepository;
import com.jjenus.tracker.notification.infrastructure.repository.NotificationTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationCommandService {
    
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationTemplateRepository templateRepository;
    
    public NotificationCommandService(
        NotificationRepository notificationRepository,
        NotificationPreferenceRepository preferenceRepository,
        NotificationTemplateRepository templateRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.templateRepository = templateRepository;
    }
    
    public void markAsRead(String notificationId) {
        Notification notification = notificationRepository.findByNotificationId(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        
        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);
    }
    
    public void markAllAsRead(String userId) {
        List<Notification> unreadNotifications = 
            notificationRepository.findUnreadByUserId(userId);
        
        Instant now = Instant.now();
        unreadNotifications.forEach(notification -> 
            notification.setReadAt(now)
        );
        
        notificationRepository.saveAll(unreadNotifications);
    }
    
    public void deleteNotification(String notificationId) {
        Notification notification = notificationRepository.findByNotificationId(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        
        notificationRepository.delete(notification);
    }
    
    public List<NotificationPreferenceResponse> updatePreferences(
        String userId, 
        UpdatePreferencesRequest request
    ) {
        // Delete existing preferences for this user
        preferenceRepository.deleteByUserId(userId);
        
        // Create new preferences
        List<NotificationPreference> newPreferences = request.getPreferences().stream()
            .map(prefDto -> {
                NotificationPreference preference = new NotificationPreference();
                preference.setUserId(userId);
                preference.setAlertType(prefDto.getAlertType());
                preference.setEnabled(prefDto.isEnabled());
                preference.setEnabledChannels(prefDto.getChannels().stream()
                    .map(NotificationChannel::valueOf)
                    .collect(Collectors.toSet()));
                return preference;
            })
            .collect(Collectors.toList());
        
        List<NotificationPreference> saved = preferenceRepository.saveAll(newPreferences);
        
        return saved.stream()
            .map(pref -> {
                NotificationPreferenceResponse response = new NotificationPreferenceResponse();
                response.setUserId(pref.getUserId());
                response.setAlertType(pref.getAlertType());
                response.setEnabled(pref.isEnabled());
                response.setEnabledChannels(pref.getEnabledChannels().stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet()));
                response.setUpdatedAt(pref.getUpdatedAt());
                return response;
            })
            .collect(Collectors.toList());
    }
    
    public NotificationTemplateResponse createTemplate(CreateTemplateRequest request) {
        NotificationTemplate template = new NotificationTemplate();
        template.setTemplateId(generateTemplateId());
        template.setName(request.getName());
        template.setTemplateType(request.getTemplateType());
        template.setChannel(NotificationChannel.valueOf(request.getChannel()));
        template.setSubjectTemplate(request.getSubjectTemplate());
        template.setBodyTemplate(request.getBodyTemplate());
        template.setLanguage(request.getLanguage());
        template.setEnabled(request.isEnabled());
        template.setVariablesDescription(request.getVariablesDescription());
        
        NotificationTemplate saved = templateRepository.save(template);
        return toTemplateResponse(saved);
    }
    
    public NotificationTemplateResponse updateTemplate(
        String templateId, 
        UpdateTemplateRequest request
    ) {
        NotificationTemplate template = templateRepository.findByTemplateId(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        
        if (request.getName() != null) template.setName(request.getName());
        if (request.getTemplateType() != null) template.setTemplateType(request.getTemplateType());
        if (request.getChannel() != null) template.setChannel(NotificationChannel.valueOf(request.getChannel()));
        if (request.getSubjectTemplate() != null) template.setSubjectTemplate(request.getSubjectTemplate());
        if (request.getBodyTemplate() != null) template.setBodyTemplate(request.getBodyTemplate());
        if (request.getLanguage() != null) template.setLanguage(request.getLanguage());
        if (request.isEnabled() != template.isEnabled()) template.setEnabled(request.isEnabled());
        if (request.getVariablesDescription() != null) {
            template.setVariablesDescription(request.getVariablesDescription());
        }
        
        NotificationTemplate updated = templateRepository.save(template);
        return toTemplateResponse(updated);
    }
    
    public void deleteTemplate(String templateId) {
        NotificationTemplate template = templateRepository.findByTemplateId(templateId)
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        
        templateRepository.delete(template);
    }
    
    private String generateTemplateId() {
        return "TMPL_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
