package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.alerting.domain.IAlertRule;
import com.jjenus.tracker.alerting.domain.AlertEvent;
import com.jjenus.tracker.core.domain.Vehicle;
import com.jjenus.tracker.shared.domain.LocationPoint;
import org.springframework.stereotype.Service;

@Service
public class AlertRuleEvaluationService {

    public AlertEvent evaluateRule(IAlertRule rule, String vehicleId, LocationPoint location) {
        // Handle null parameters
        if (rule == null || vehicleId == null || location == null) {
            return null;
        }
        return rule.evaluate(vehicleId, location);
    }

    public boolean validateRuleConfiguration(IAlertRule rule) {
        if (rule == null) return false;

        String ruleKey = rule.getRuleKey();
        String ruleName = rule.getRuleName();

        // More comprehensive validation
        if (ruleKey == null || ruleKey.trim().isEmpty()) return false;
        if (ruleName == null || ruleName.trim().isEmpty()) return false;

        // Validate that ruleKey doesn't contain invalid characters
        if (!ruleKey.matches("^[a-zA-Z0-9_]+$")) return false;

        // Validate priority is reasonable
        int priority = rule.getPriority();
        return priority >= 1 && priority <= 100;
    }
}