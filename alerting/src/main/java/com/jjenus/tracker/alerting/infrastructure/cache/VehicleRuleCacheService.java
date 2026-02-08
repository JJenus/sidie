@Component
public class AlertingEngine {
    private final AlertRuleQueryService ruleQueryService;
    private final EventPublisher eventPublisher;
    private final AlertRuleFactory alertRuleFactory;
    private final Logger logger = LoggerFactory.getLogger(AlertingEngine.class);

    public AlertingEngine(
            EventPublisher eventPublisher,
            AlertRuleFactory alertRuleFactory,
            AlertRuleQueryService ruleQueryService
           ) {  // Updated
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