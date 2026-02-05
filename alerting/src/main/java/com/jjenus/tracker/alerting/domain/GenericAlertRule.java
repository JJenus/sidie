package com.jjenus.tracker.alerting.domain;

import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.shared.domain.LocationPoint;
import java.util.Map;

public class GenericAlertRule implements IAlertRule {
    private final String ruleKey;
    private final String ruleName;
    private final Map<String, Object> parameters;
    private boolean enabled;
    private final int priority;

    public GenericAlertRule(AlertRule entity, Map<String, Object> parameters) {
        this.ruleKey = entity.getRuleKey();
        this.ruleName = entity.getRuleName();
        this.parameters = parameters;
        this.enabled = entity.isEnabled();
        this.priority = entity.getPriority();
    }

    @Override
    public AlertDetectedEvent evaluate(String vehicleId, LocationPoint newLocation) {
        // Custom rule evaluation logic based on parameters
        // Could be implemented using scripting engine or rule engine
        return null; // Override in specific implementations
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

    public Map<String, Object> getParameters() { return parameters; }
}