package com.jjenus.tracker.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification_templates", indexes = {
    @Index(name = "idx_template_channel", columnList = "channel"),
    @Index(name = "idx_template_type", columnList = "templateType"),
    @Index(name = "idx_template_enabled", columnList = "enabled")
})
public class NotificationTemplate {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String templateId;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String templateType; // SPEED_ALERT, GEOFENCE_ALERT, etc.
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;
    
    @Column(columnDefinition = "TEXT")
    private String subjectTemplate;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String bodyTemplate;
    
    private String language = "en";
    private boolean enabled = true;
    
    @Column(columnDefinition = "TEXT")
    private String variablesDescription; // JSON schema of expected variables
    
    private String createdBy = "system";
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }
    
    public NotificationChannel getChannel() { return channel; }
    public void setChannel(NotificationChannel channel) { this.channel = channel; }
    
    public String getSubjectTemplate() { return subjectTemplate; }
    public void setSubjectTemplate(String subjectTemplate) { this.subjectTemplate = subjectTemplate; }
    
    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }
    
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public String getVariablesDescription() { return variablesDescription; }
    public void setVariablesDescription(String variablesDescription) { this.variablesDescription = variablesDescription; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
