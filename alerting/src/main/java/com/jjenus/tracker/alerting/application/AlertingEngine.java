package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.alerting.domain.IAlertRule;
import com.jjenus.tracker.alerting.domain.AlertEvent;
import com.jjenus.tracker.core.domain.Vehicle;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.shared.exception.ValidationException;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
import com.jjenus.tracker.alerting.exception.AlertException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Comparator;

@Component
public class AlertingEngine {
    private final List<IAlertRule> activeRules;
    private final EventPublisher eventPublisher;
    private final AlertRuleEvaluationService evaluationService;

    public AlertingEngine(EventPublisher eventPublisher,
                         AlertRuleEvaluationService evaluationService) {
        this.activeRules = new CopyOnWriteArrayList<>();
        this.eventPublisher = eventPublisher;
        this.evaluationService = evaluationService;
    }

    public void processVehicleUpdate(String vehicleId, LocationPoint newLocation) {
        if (vehicleId == null || newLocation == null) {
            throw new com.jjenus.tracker.shared.exception.ValidationException(
                "ALERT_INVALID_INPUT",
                "Vehicle and location cannot be null"
            );
        }

        List<IAlertRule> sortedRules = activeRules.stream()
            .filter(IAlertRule::isEnabled)
            .sorted(Comparator.comparingInt(IAlertRule::getPriority).reversed())
            .toList();

        for (IAlertRule rule : sortedRules) {
            try {
                AlertEvent alert = evaluationService.evaluateRule(rule, vehicleId, newLocation);

                if (alert != null) {
                    System.out.println("Alert triggered: " + alert.getRuleKey() +
                                     " for vehicle " + vehicleId);

                    eventPublisher.publish(alert);

//                    vehicle.addAlert(alert.getMessage());
                }
            } catch (AlertException e) {
                System.err.println("Alert evaluation error for rule " + rule.getRuleKey() + ": " + e.getMessage());
                throw e;
            } catch (Exception e) {
                System.err.println("Unexpected error evaluating rule " + rule.getRuleKey() + ": " + e.getMessage());
                throw AlertException.evaluationError(rule.getRuleKey(), e.getMessage());
            }
        }
    }

    public void registerRule(IAlertRule rule) {
        if (rule == null) {
            throw new ValidationException(
                "ALERT_RULE_NULL",
                "Alert rule cannot be null"
            );
        }

        boolean ruleExists = activeRules.stream()
            .anyMatch(r -> r.getRuleKey().equals(rule.getRuleKey()));

        if (ruleExists) {
            throw AlertException.ruleAlreadyExists(rule.getRuleKey());
        }

        if (!evaluationService.validateRuleConfiguration(rule)) {
            throw AlertException.invalidConfiguration(rule.getRuleKey(), "Invalid configuration");
        }

        activeRules.add(rule);
        System.out.println("Registered rule: " + rule.getRuleName());
    }

    public void unregisterRule(String ruleKey) {
        boolean removed = activeRules.removeIf(rule -> rule.getRuleKey().equals(ruleKey));
        if (!removed) {
            throw AlertException.ruleNotFound(ruleKey);
        }
        System.out.println("Unregistered rule: " + ruleKey);
    }

    public List<IAlertRule> getActiveRules() {
        return List.copyOf(activeRules);
    }

    public void clearRules() {
        activeRules.clear();
        System.out.println("Cleared all alert rules");
    }
}
