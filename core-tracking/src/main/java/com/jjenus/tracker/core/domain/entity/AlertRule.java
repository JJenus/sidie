package com.jjenus.tracker.core.domain.entity;

import com.jjenus.tracker.core.domain.enums.AlertRuleType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "alert_rules")
public class AlertRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;
    
    @Column(name = "rule_key", length = 100, unique = true, nullable = false)
    private String ruleKey;
    
    @Column(name = "rule_name", length = 200, nullable = false)
    private String ruleName;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", length = 50, nullable = false)
    private AlertRuleType ruleType;
    
    @Column(name = "is_enabled")
    private Boolean isEnabled = true;
    
    @Column(name = "priority")
    private Integer priority = 5;
    
    @Column(name = "parameters", columnDefinition = "JSONB", nullable = false)
    private String parameters; // JSON configuration
    
    @Column(name = "conditions", columnDefinition = "JSONB")
    private String conditions; // Additional conditions
    
    @Column(name = "actions", columnDefinition = "JSONB")
    private String actions; // Actions to take when triggered
    
    @Column(name = "notification_channels", columnDefinition = "JSONB")
    private String notificationChannels; // How to notify
    
    @Column(name = "cooldown_minutes")
    private Integer cooldownMinutes = 5; // Prevent spam
    
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    @Column(name = "created_at")
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
    
    // Business methods
    public boolean isInCooldown(Instant lastTriggered) {
        if (cooldownMinutes == null || cooldownMinutes == 0) return false;
        if (lastTriggered == null) return false;
        
        return Instant.now().isBefore(
            lastTriggered.plusSeconds(cooldownMinutes * 60L)
        );
    }
    
    // Getters and Setters
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }
    
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    
    public AlertRuleType getRuleType() { return ruleType; }
    public void setRuleType(AlertRuleType ruleType) { this.ruleType = ruleType; }
    
    public Boolean getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Boolean isEnabled) { this.isEnabled = isEnabled; }
    
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    // Additional getters/setters
    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    
    public String getActions() { return actions; }
    public void setActions(String actions) { this.actions = actions; }
    
    public String getNotificationChannels() { return notificationChannels; }
    public void setNotificationChannels(String notificationChannels) { 
        this.notificationChannels = notificationChannels; 
    }
    
    public Integer getCooldownMinutes() { return cooldownMinutes; }
    public void setCooldownMinutes(Integer cooldownMinutes) { this.cooldownMinutes = cooldownMinutes; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}