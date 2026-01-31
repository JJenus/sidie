package com.jjenus.tracker.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "notification_preferences", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "alertType"}))
public class NotificationPreference {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String alertType; // OVERSPEED, GEOFENCE_EXIT, etc.
    
    @ElementCollection
    @CollectionTable(name = "preference_channels", 
                     joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "channel")
    @Enumerated(EnumType.STRING)
    private Set<NotificationChannel> enabledChannels = new HashSet<>();
    
    private boolean enabled = true;
    
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    // Domain methods
    public void enableChannel(NotificationChannel channel) {
        enabledChannels.add(channel);
    }
    
    public void disableChannel(NotificationChannel channel) {
        enabledChannels.remove(channel);
    }
    
    public boolean isChannelEnabled(NotificationChannel channel) {
        return enabled && enabledChannels.contains(channel);
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    
    public Set<NotificationChannel> getEnabledChannels() { return enabledChannels; }
    public void setEnabledChannels(Set<NotificationChannel> enabledChannels) { 
        this.enabledChannels = enabledChannels; 
    }
    
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
