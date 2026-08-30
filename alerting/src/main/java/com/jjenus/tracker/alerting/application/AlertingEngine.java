package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.alerting.application.dedup.AlertDeduplicator;
import com.jjenus.tracker.alerting.application.service.AlertRuleEvaluationService;
import com.jjenus.tracker.alerting.domain.IAlertRule;
import com.jjenus.tracker.alerting.domain.AlertDetectedEvent;
import com.jjenus.tracker.alerting.domain.GeofenceRule;
import com.jjenus.tracker.alerting.domain.RuleStateStore;
import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.application.factory.AlertRuleFactory;
import com.jjenus.tracker.alerting.infrastructure.cache.VehicleRuleCacheService;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.shared.exception.ValidationException;
import com.jjenus.tracker.shared.metrics.MetricsRegistry;
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
    private final AlertRuleFactory ruleFactory;
    private final RuleStateStore stateStore;
    private final AlertDeduplicator deduplicator;
    private final MetricsRegistry metrics;
    private final Logger logger = LoggerFactory.getLogger(AlertingEngine.class);

    public AlertingEngine(
            EventPublisher eventPublisher,
            AlertRuleEvaluationService evaluationService,
            VehicleRuleCacheService vehicleRuleCacheService,
            AlertRuleFactory ruleFactory,
            RuleStateStore stateStore,
            AlertDeduplicator deduplicator,
            MetricsRegistry metrics) {
        this.vehicleRuleCacheService = vehicleRuleCacheService;
        this.eventPublisher = eventPublisher;
        this.evaluationService = evaluationService;
        this.ruleFactory = ruleFactory;
        this.stateStore = stateStore;
        this.deduplicator = deduplicator;
        this.metrics = metrics;
    }

    public void processVehicleUpdate(String vehicleId, LocationPoint newLocation) {
        if (vehicleId == null || newLocation == null) {
            throw new ValidationException(
                    "ALERT_INVALID_INPUT",
                    "Vehicle and location cannot be null"
            );
        }

        if (!vehicleRuleCacheService.hasRulesCached(vehicleId)) {
            logger.debug("Vehicle {} has no active rules (cached index), skipping", vehicleId);
            return;
        }

        if (!vehicleRuleCacheService.hasActiveRules(vehicleId)) {
            return;
        }

        List<AlertRule> vehicleRules = vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId);

        if (vehicleRules.isEmpty()) {
            return;
        }

        logger.debug("Processing {} rules for vehicle {}", vehicleRules.size(), vehicleId);

        for (AlertRule rule : vehicleRules) {
            try {
                IAlertRule domainRule = ruleFactory.createDomainRule(rule, vehicleId);
                if (domainRule == null) {
                    continue;
                }

                AlertDetectedEvent alert = evaluationService.evaluateRule(domainRule, vehicleId, newLocation);

                if (alert != null && deduplicator.tryAcquire(alert)) {
                    logger.info("Alert triggered: {} for vehicle {}", alert.getRuleKey(), vehicleId);
                    eventPublisher.publish(alert);
                    metrics.increment("alert.triggered", "type", alert.getAlertType().name());
                }

                if (domainRule instanceof GeofenceRule geo) {
                    stateStore.setGeofenceWasInside(rule.getRuleKey(), vehicleId, geo.isWasInside());
                }
            } catch (AlertException e) {
                logger.error("Alert evaluation error for rule {}: {}", rule.getRuleKey(), e.getMessage());
                metrics.increment("alert.error", "rule", rule.getRuleKey());
            } catch (Exception e) {
                logger.error("Unexpected error evaluating rule {}: {}", rule.getRuleKey(), e.getMessage());
            }
        }
    }

    public void invalidateVehicleCache(String vehicleId) {
        vehicleRuleCacheService.invalidateVehicleRules(vehicleId);
    }

    public void invalidateAllCache() {
        vehicleRuleCacheService.invalidateAllVehicleRules();
    }

    public void refreshVehicleRules(String vehicleId) {
        vehicleRuleCacheService.invalidateVehicleRules(vehicleId);
        vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId);
    }
}
