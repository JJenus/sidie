package com.jjenus.tracker.notification.api;

import com.jjenus.tracker.notification.api.dto.*;
import com.jjenus.tracker.notification.application.NotificationQueryService;
import com.jjenus.tracker.notification.application.NotificationCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Notification management endpoints")
public class NotificationController {
    
    private final NotificationQueryService queryService;
    private final NotificationCommandService commandService;
    
    public NotificationController(
        NotificationQueryService queryService,
        NotificationCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }
    
    @GetMapping
    @Operation(summary = "Get notifications with filtering")
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @Parameter(name = "userId", description = "User ID filter")
            @RequestParam(required = false) String userId,
            
            @Parameter(name = "status", description = "Notification status filter")
            @RequestParam(required = false) String status,
            
            @Parameter(name = "channel", description = "Notification channel filter")
            @RequestParam(required = false) String channel,
            
            @Parameter(name = "alertId", description = "Alert ID filter")
            @RequestParam(required = false) String alertId,
            
            Pageable pageable) {
        
        Page<NotificationResponse> notifications = queryService.findNotifications(
            userId, status, channel, alertId, pageable
        );
        return ResponseEntity.ok(notifications);
    }
    
    @GetMapping("/{notificationId}")
    @Operation(summary = "Get notification by ID")
    public ResponseEntity<NotificationResponse> getNotificationById(
            @Parameter(name = "notificationId", description = "Notification ID")
            @PathVariable String notificationId) {
        
        NotificationResponse notification = queryService.getNotificationById(notificationId);
        return ResponseEntity.ok(notification);
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get notifications for a specific user")
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            @Parameter(name = "userId", description = "User ID")
            @PathVariable String userId,
            
            @Parameter(name = "unreadOnly", description = "Return only unread notifications")
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            
            Pageable pageable) {
        
        Page<NotificationResponse> notifications = queryService.getUserNotifications(
            userId, unreadOnly, pageable
        );
        return ResponseEntity.ok(notifications);
    }
    
    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(name = "notificationId", description = "Notification ID")
            @PathVariable String notificationId) {
        commandService.markAsRead(notificationId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/user/{userId}/read-all")
    @Operation(summary = "Mark all user notifications as read")
    public ResponseEntity<Void> markAllAsRead(
            @Parameter(name = "userId", description = "User ID")
            @PathVariable String userId) {
        commandService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete notification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteNotification(
            @Parameter(name = "notificationId", description = "Notification ID")
            @PathVariable String notificationId) {
        commandService.deleteNotification(notificationId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/preferences/{userId}")
    @Operation(summary = "Get notification preferences for user")
    public ResponseEntity<List<NotificationPreferenceResponse>> getUserPreferences(
            @Parameter(name = "userId", description = "User ID")
            @PathVariable String userId) {
        
        List<NotificationPreferenceResponse> preferences = 
            queryService.getUserPreferences(userId);
        return ResponseEntity.ok(preferences);
    }
    
    @PutMapping("/preferences/{userId}")
    @Operation(summary = "Update notification preferences")
    public ResponseEntity<List<NotificationPreferenceResponse>> updatePreferences(
            @Parameter(name = "userId", description = "User ID")
            @PathVariable String userId,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        
        List<NotificationPreferenceResponse> updated = 
            commandService.updatePreferences(userId, request);
        return ResponseEntity.ok(updated);
    }
    
    @GetMapping("/templates")
    @Operation(summary = "Get notification templates")
    public ResponseEntity<Page<NotificationTemplateResponse>> getTemplates(
            @Parameter(name = "templateType", description = "Template type filter")
            @RequestParam(required = false) String templateType,
            
            @Parameter(name = "channel", description = "Channel filter")
            @RequestParam(required = false) String channel,
            
            Pageable pageable) {
        
        Page<NotificationTemplateResponse> templates = 
            queryService.getTemplates(templateType, channel, pageable);
        return ResponseEntity.ok(templates);
    }
    
    @PostMapping("/templates")
    @Operation(summary = "Create notification template")
    public ResponseEntity<NotificationTemplateResponse> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request) {
        
        NotificationTemplateResponse template = commandService.createTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(template);
    }
    
    @PutMapping("/templates/{templateId}")
    @Operation(summary = "Update notification template")
    public ResponseEntity<NotificationTemplateResponse> updateTemplate(
            @Parameter(name = "templateId", description = "Template ID")
            @PathVariable String templateId,
            @Valid @RequestBody UpdateTemplateRequest request) {
        
        NotificationTemplateResponse template = 
            commandService.updateTemplate(templateId, request);
        return ResponseEntity.ok(template);
    }
    
    @DeleteMapping("/templates/{templateId}")
    @Operation(summary = "Delete notification template")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteTemplate(
            @Parameter(name = "templateId", description = "Template ID")
            @PathVariable String templateId) {
        commandService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
}