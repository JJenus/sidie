package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.alerting.application.service.AlertRuleEvaluationService;
import com.jjenus.tracker.alerting.domain.IAlertRule;
import com.jjenus.tracker.alerting.domain.AlertDetectedEvent;
import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.infrastructure.cache.VehicleRuleCacheService;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.shared.exception.ValidationException;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
import com.jjenus.tracker.alerting.exception.AlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AlertingEngine {
    private final VehicleRuleCacheService vehicleRuleCacheService;
    private final EventPublisher eventPublisher;
    private final AlertRuleEvaluationService evaluationService;
    private final Logger logger = LoggerFactory.getLogger(AlertingEngine.class);

    public AlertingEngine(
            EventPublisher eventPublisher,
            AlertRuleEvaluationService evaluationService,
            VehicleRuleCacheService vehicleRuleCacheService) {
        this.vehicleRuleCacheService = vehicleRuleCacheService;
        this.eventPublisher = eventPublisher;
        this.evaluationService = evaluationService;
    }

    public void processVehicleUpdate(String vehicleId, LocationPoint newLocation) {
        if (vehicleId == null || newLocation == null) {
            throw new ValidationException(
                    "ALERT_INVALID_INPUT",
                    "Vehicle and location cannot be null"
            );
        }

        // QUICK CHECK 1: Using cached index
        if (!vehicleRuleCacheService.hasRulesCached(vehicleId)) {
            logger.debug("Vehicle {} has no active rules (cached index), skipping", vehicleId);
            return;
        }

        // QUICK CHECK 2: Full cache check
        if (!vehicleRuleCacheService.hasActiveRules(vehicleId)) {
            return;
        }

        // Get pre-sorted rules from Redis cache
        List<AlertRule> vehicleRules = vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId);

        if (vehicleRules.isEmpty()) {
            return;
        }

        logger.debug("Processing {} rules for vehicle {}", vehicleRules.size(), vehicleId);

        for (AlertRule rule : vehicleRules) {
            try {
                IAlertRule domainRule = convertToDomainRule(rule);
                AlertDetectedEvent alert = evaluationService.evaluateRule(domainRule, vehicleId, newLocation);

                if (alert != null) {
                    logger.info("Alert triggered: {} for vehicle {}",
                            alert.getRuleKey(), vehicleId);
                    eventPublisher.publish(alert);
                }
            } catch (AlertException e) {
                logger.error("Alert evaluation error for rule {}: {}", rule.getRuleKey(), e.getMessage());
            } catch (Exception e) {
                logger.error("Unexpected error evaluating rule {}: {}", rule.getRuleKey(), e.getMessage());
            }
        }
    }

    private IAlertRule convertToDomainRule(AlertRule entityRule) {
        return new IAlertRule() {
            @Override
            public AlertDetectedEvent evaluate(String vehicleId, LocationPoint newLocation) {
                return evaluationService.evaluateRule(this, vehicleId, newLocation);
            }

            @Override
            public String getRuleKey() { return entityRule.getRuleKey(); }

            @Override
            public String getRuleName() { return entityRule.getRuleName(); }

            @Override
            public boolean isEnabled() { return Boolean.TRUE.equals(entityRule.isEnabled()); }

            @Override
            public void setEnabled(boolean enabled) { entityRule.setIsEnabled(enabled); }

            @Override
            public int getPriority() { return entityRule.getPriority() != null ? entityRule.getPriority() : 5; }
        };
    }

    // Cache management methods
    public void invalidateVehicleCache(String vehicleId) {
        vehicleRuleCacheService.invalidateVehicleRules(vehicleId);
    }

    public void invalidateAllCache() {
        vehicleRuleCacheService.invalidateAllVehicleRules();
    }

    public void refreshVehicleRules(String vehicleId) {
        vehicleRuleCacheService.invalidateVehicleRules(vehicleId);
        vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId); // Re-cache
    }
}