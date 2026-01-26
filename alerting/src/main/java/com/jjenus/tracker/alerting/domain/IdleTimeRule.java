package com.jjenus.tracker.alerting.domain;

import com.jjenus.tracker.shared.domain.LocationPoint;
import java.time.Duration;

public class IdleTimeRule implements IAlertRule {
    private final String ruleKey;
    private final String ruleName;
    private final Duration maxIdleTime;
    private boolean enabled;
    private final int priority;
    
    public IdleTimeRule(String ruleKey, Duration maxIdleTime) {
        this.ruleKey = ruleKey;
        this.ruleName = "Idle Time Rule";
        this.maxIdleTime = maxIdleTime;
        this.enabled = true;
        this.priority = 1;
    }
    
    @Override
    public AlertEvent evaluate(String vehicleId, LocationPoint newLocation) {
        if (!enabled) return null;
        
        Duration idleDuration = Duration.ofHours(3);
        
        if (idleDuration.compareTo(maxIdleTime) > 0) {
            String message = String.format(
                "Vehicle %s has been idle for %d minutes (max allowed: %d minutes)",
                vehicleId,
                idleDuration.toMinutes(),
                maxIdleTime.toMinutes()
            );
            
            return new AlertEvent(
                ruleKey,
                vehicleId,
                message,
                AlertSeverity.INFO,
                newLocation
            );
        }
        return null;
    }
    
    @Override
    public String getRuleKey() { return ruleKey; }
    
    @Override
    public String getRuleName() { return ruleName; }
    
    @Override
    public boolean isEnabled() { return enabled; }
    
    @Override
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    @Override
    public int getPriority() { return priority; }
    
    public Duration getMaxIdleTime() { return maxIdleTime; }
}
