package com.jjenus.tracker.notification.domain.entity;

import com.jjenus.tracker.notification.domain.enums.DeliveryStatus;
import com.jjenus.tracker.notification.domain.enums.NotificationChannel;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_alert_id", columnList = "alertId"),
    @Index(name = "idx_notification_recipient", columnList = "recipient"),
    @Index(name = "idx_notification_status", columnList = "status"),
    @Index(name = "idx_notification_created", columnList = "createdAt DESC")
})
public class Notification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String notificationId;
    
    @Column(nullable = false)
    private long alertId; // From alerting context
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;
    
    @Column(nullable = false)
    private String recipient; // User ID, email, phone number, etc.
    
    private String templateId;
    
    @Column(columnDefinition = "TEXT")
    private String templateVariables; // JSON
    
    private String title;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.PENDING;
    
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant readAt;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    private Integer retryCount = 0;
    private Integer maxRetries = 3;
    
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    
    // Audit fields
    private String createdBy = "system";
    private String updatedBy = "system";
    
    @PrePersist
    protected void onCreate() {
        if (notificationId == null) {
            notificationId = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    // Domain methods
    public void markAsSending() {
        if (status != DeliveryStatus.PENDING && status != DeliveryStatus.RETRYING) {
            throw new IllegalStateException(
                String.format("Cannot mark as sending from status: %s", status)
            );
        }
        this.status = DeliveryStatus.SENDING;
        this.sentAt = Instant.now();
    }
    
    public void markAsSent() {
        this.status = DeliveryStatus.SENT;
        this.sentAt = Instant.now();
    }
    
    public void markAsDelivered() {
        if (status != DeliveryStatus.SENT) {
            throw new IllegalStateException(
                String.format("Cannot mark as delivered from status: %s", status)
            );
        }
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = Instant.now();
    }
    
    public void markAsFailed(String error) {
        this.status = DeliveryStatus.FAILED;
        this.errorMessage = error;
        this.retryCount++;
    }
    
    public void markForRetry() {
        if (retryCount >= maxRetries) {
            this.status = DeliveryStatus.DISCARDED;
        } else {
            this.status = DeliveryStatus.RETRYING;
        }
    }
    
    public boolean canRetry() {
        return status.canRetry() && retryCount < maxRetries;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
    
    public long getAlertId() { return alertId; }
    public void setAlertId(long alertId) { this.alertId = alertId; }
    
    public NotificationChannel getChannel() { return channel; }
    public void setChannel(NotificationChannel channel) { this.channel = channel; }
    
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    
    public String getTemplateVariables() { return templateVariables; }
    public void setTemplateVariables(String templateVariables) { this.templateVariables = templateVariables; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
