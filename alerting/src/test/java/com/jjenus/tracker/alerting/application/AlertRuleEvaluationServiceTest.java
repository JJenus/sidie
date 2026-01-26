package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.alerting.application.service.AlertRuleEvaluationService;
import com.jjenus.tracker.alerting.domain.IAlertRule;
import com.jjenus.tracker.alerting.domain.AlertEvent;
import com.jjenus.tracker.alerting.domain.AlertSeverity;
import com.jjenus.tracker.alerting.domain.MaxSpeedRule;
import com.jjenus.tracker.core.domain.Vehicle;
import com.jjenus.tracker.shared.domain.LocationPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class AlertRuleEvaluationServiceTest {

    private AlertRuleEvaluationService evaluationService;
    private Vehicle testVehicle;
    private LocationPoint testLocation;
    private Instant testTime;

    @BeforeEach
    void setUp() {
        evaluationService = new AlertRuleEvaluationService();
        testTime = Instant.now();
        testVehicle = new Vehicle("VEH-001");
        testLocation = new LocationPoint(40.7128, -74.0060, 30.0f, testTime);
    }

    @Test
    void testEvaluateRule() {
        MaxSpeedRule speedRule = new MaxSpeedRule("SPEED_100", 100.0f);

        // Below threshold - no alert
        AlertEvent noAlert = evaluationService.evaluateRule(speedRule, testVehicle.getVehicleId(), testLocation);
        assertNull(noAlert);

        // Above threshold - alert
        LocationPoint speedingLocation = new LocationPoint(40.7128, -74.0060, 120.0f, testTime);
        AlertEvent alert = evaluationService.evaluateRule(speedRule, testVehicle.getVehicleId(), speedingLocation);

        assertNotNull(alert);
        assertEquals("SPEED_100", alert.getRuleKey());
        assertEquals("VEH-001", alert.getVehicleId());
        assertEquals(AlertSeverity.WARNING, alert.getSeverity());
        assertTrue(alert.getMessage().contains("120.0"));
        assertEquals(speedingLocation, alert.getLocation());
    }

    @Test
    void testEvaluateRuleWithNullRule() {
        AlertEvent result = evaluationService.evaluateRule(null, testVehicle.getVehicleId(), testLocation);
        assertNull(result);
    }

    @Test
    void testEvaluateRuleWithNullVehicle() {
        MaxSpeedRule speedRule = new MaxSpeedRule("SPEED_100", 100.0f);

        AlertEvent result = evaluationService.evaluateRule(speedRule, null, testLocation);
        assertNull(result);
    }

    @Test
    void testEvaluateRuleWithNullLocation() {
        MaxSpeedRule speedRule = new MaxSpeedRule("SPEED_100", 100.0f);

        AlertEvent result = evaluationService.evaluateRule(speedRule, testVehicle.getVehicleId(), null);
        assertNull(result);
    }

    @Test
    void testValidateRuleConfigurationValid() {
        MaxSpeedRule validRule = new MaxSpeedRule("SPEED_100", 100.0f);

        boolean isValid = evaluationService.validateRuleConfiguration(validRule);

        assertTrue(isValid);
    }

    @Test
    void testValidateRuleConfigurationNull() {
        boolean isValid = evaluationService.validateRuleConfiguration(null);

        assertFalse(isValid);
    }

    @Test
    void testValidateRuleConfigurationEmptyKey() {
        IAlertRule emptyKeyRule = new IAlertRule() {
            @Override
            public AlertEvent evaluate(String vehicleId, LocationPoint newLocation) {
                return null;
            }

            @Override
            public String getRuleKey() {
                return "";
            }

            @Override
            public String getRuleName() {
                return "Test Rule";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void setEnabled(boolean enabled) {
            }

            @Override
            public int getPriority() {
                return 1;
            }
        };

        boolean isValid = evaluationService.validateRuleConfiguration(emptyKeyRule);

        assertFalse(isValid);
    }

    @Test
    void testValidateRuleConfigurationNullKey() {
        IAlertRule nullKeyRule = new IAlertRule() {
            @Override
            public AlertEvent evaluate(String vehicleId, LocationPoint newLocation) {
                return null;
            }

            @Override
            public String getRuleKey() {
                return null;
            }

            @Override
            public String getRuleName() {
                return "Test Rule";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void setEnabled(boolean enabled) {
            }

            @Override
            public int getPriority() {
                return 1;
            }
        };

        boolean isValid = evaluationService.validateRuleConfiguration(nullKeyRule);

        assertFalse(isValid);
    }

    @Test
    void testValidateRuleConfigurationEmptyName() {
        IAlertRule emptyNameRule = new IAlertRule() {
            @Override
            public AlertEvent evaluate(String vehicleId, LocationPoint newLocation) {
                return null;
            }

            @Override
            public String getRuleKey() {
                return "TEST_RULE";
            }

            @Override
            public String getRuleName() {
                return "";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void setEnabled(boolean enabled) {
            }

            @Override
            public int getPriority() {
                return 1;
            }
        };

        boolean isValid = evaluationService.validateRuleConfiguration(emptyNameRule);

        assertFalse(isValid);
    }

    @Test
    void testValidateRuleConfigurationNullName() {
        IAlertRule nullNameRule = new IAlertRule() {
            @Override
            public AlertEvent evaluate(String vehicleId, LocationPoint newLocation) {
                return null;
            }

            @Override
            public String getRuleKey() {
                return "TEST_RULE";
            }

            @Override
            public String getRuleName() {
                return null;
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void setEnabled(boolean enabled) {
            }

            @Override
            public int getPriority() {
                return 1;
            }
        };

        boolean isValid = evaluationService.validateRuleConfiguration(nullNameRule);

        assertFalse(isValid);
    }

    @Test
    void testEvaluateRuleWithDisabledRule() {
        MaxSpeedRule disabledRule = new MaxSpeedRule("SPEED_100", 100.0f);
        disabledRule.setEnabled(false);

        LocationPoint speedingLocation = new LocationPoint(40.7128, -74.0060, 120.0f, testTime);
        AlertEvent alert = evaluationService.evaluateRule(disabledRule, testVehicle.getVehicleId(), speedingLocation);

        assertNull(alert);
    }

    @Test
    void testEvaluateRuleWithCustomRule() {
        // Test with a custom rule implementation
        IAlertRule customRule = new IAlertRule() {
            @Override
            public AlertEvent evaluate(String vehicleId, LocationPoint newLocation) {
                if (vehicleId == null || newLocation == null) {
                    return null;
                }
                if (vehicleId.equals("SPECIAL_VEHICLE")) {
                    return new AlertEvent("SPECIAL_RULE", vehicleId,
                            "Special vehicle detected", AlertSeverity.INFO, newLocation);
                }
                return null;
            }

            @Override
            public String getRuleKey() {
                return "SPECIAL_RULE";
            }

            @Override
            public String getRuleName() {
                return "Special Vehicle Rule";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void setEnabled(boolean enabled) {
            }

            @Override
            public int getPriority() {
                return 5;
            }
        };

        // Test with special vehicle
        Vehicle specialVehicle = new Vehicle("SPECIAL_VEHICLE");
        AlertEvent alert = evaluationService.evaluateRule(customRule, specialVehicle.getVehicleId(), testLocation);

        assertNotNull(alert);
        assertEquals("SPECIAL_RULE", alert.getRuleKey());
        assertEquals("SPECIAL_VEHICLE", alert.getVehicleId());
        assertEquals("Special vehicle detected", alert.getMessage());

        // Test with regular vehicle
        AlertEvent noAlert = evaluationService.evaluateRule(customRule, testVehicle.getVehicleId(), testLocation);
        assertNull(noAlert);
    }

    @Test
    void testRulePriorityConsideration() {
        // This service doesn't handle priority, but we can verify rules have priority
        MaxSpeedRule rule = new MaxSpeedRule("SPEED_100", 100.0f);
        assertEquals(2, rule.getPriority());

        // Priority is used by AlertingEngine for evaluation order
        assertTrue(rule.getPriority() >= 1);
    }

    @Test
    void testValidateRuleConfigurationInvalidPriority() {
        IAlertRule invalidPriorityRule = new IAlertRule() {
            @Override
            public AlertEvent evaluate(String vehicleId, LocationPoint newLocation) {
                return null;
            }

            @Override
            public String getRuleKey() {
                return "TEST_RULE";
            }

            @Override
            public String getRuleName() {
                return "Test Rule";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void setEnabled(boolean enabled) {
            }

            @Override
            public int getPriority() {
                return 0; // Invalid - less than 1
            }
        };

        boolean isValid = evaluationService.validateRuleConfiguration(invalidPriorityRule);
        assertFalse(isValid);
    }

    @Test
    void testValidateRuleConfigurationInvalidKeyCharacters() {
        IAlertRule invalidKeyRule = new IAlertRule() {
            @Override
            public AlertEvent evaluate(String vehicleId, LocationPoint newLocation) {
                return null;
            }

            @Override
            public String getRuleKey() {
                return "TEST-RULE"; // Contains hyphen
            }

            @Override
            public String getRuleName() {
                return "Test Rule";
            }

            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public void setEnabled(boolean enabled) {
            }

            @Override
            public int getPriority() {
                return 1;
            }
        };

        boolean isValid = evaluationService.validateRuleConfiguration(invalidKeyRule);
        assertFalse(isValid);
    }
}