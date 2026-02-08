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
    private final AlertRuleQueryService ruleQueryService;
    private final EventPublisher eventPublisher;
    private final AlertRuleFactory alertRuleFactory;
    private final Logger logger = LoggerFactory.getLogger(AlertingEngine.class);

    public AlertingEngine(
            EventPublisher eventPublisher,
            AlertRuleFactory alertRuleFactory,
            AlertRuleQueryService ruleQueryService) {
        this.ruleQueryService = ruleQueryService;
        this.eventPublisher = eventPublisher;
        this.alertRuleFactory = alertRuleFactory;
    }

    public void processVehicleUpdate(String vehicleId, LocationPoint newLocation) {
        if (vehicleId == null || newLocation == null) {
            throw new ValidationException(
                    "ALERT_INVALID_INPUT",
                    "Vehicle and location cannot be null"
            );
        }

        List<AlertRule> vehicleRules = ruleQueryService.getActiveRulesForVehicle(vehicleId);

        if (vehicleRules.isEmpty()) {
            return;
        }

        logger.debug("Processing {} rules for vehicle {}", vehicleRules.size(), vehicleId);

        for (AlertRule rule : vehicleRules) {
            try {
                IAlertRule domainRule = alertRuleFactory.createDomainRule(rule, vehicleId);
                if (domainRule == null) {
                    continue;
                }
                
                AlertDetectedEvent alert = domainRule.evaluate(vehicleId, newLocation);

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
}