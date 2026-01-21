package com.jjenus.tracker.alerting.domain;

import com.jjenus.tracker.core.domain.Vehicle;
import com.jjenus.tracker.shared.domain.LocationPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class MaxSpeedRuleTest {

    private MaxSpeedRule rule;
    private Vehicle vehicle;
    private Instant testTime;

    @BeforeEach
    void setUp() {
        rule = new MaxSpeedRule("SPEED_100", 100.0f);
        vehicle = new Vehicle("VEH-001");
        testTime = Instant.now();
    }

    @Test
    void testRuleCreation() {
        assertEquals("SPEED_100", rule.getRuleKey());
        assertEquals("Maximum Speed Rule", rule.getRuleName());
        assertEquals(100.0f, rule.getThresholdSpeed(), 0.001);
        assertTrue(rule.isEnabled());
        assertEquals(2, rule.getPriority());
    }

    @Test
    void testEvaluateNoAlertWhenBelowThreshold() {
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 80.0f, testTime);

        AlertEvent alert = rule.evaluate(vehicle.getVehicleId(), location);

        assertNull(alert);
    }

    @Test
    void testEvaluateAlertWhenAtThreshold() {
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 120.0f, testTime);

        AlertEvent alert = rule.evaluate(vehicle.getVehicleId(), location);

        assertNotNull(alert);
        assertEquals("SPEED_100", alert.getRuleKey());
        assertEquals("VEH-001", alert.getVehicleId());
        assertEquals(AlertSeverity.WARNING, alert.getSeverity());
        assertTrue(alert.getMessage().contains("100.0"));
        assertTrue(alert.getMessage().contains("100.0")); // threshold
        assertEquals(location, alert.getLocation());
    }

    @Test
    void testEvaluateAlertWhenAboveThreshold() {
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 120.0f, testTime);

        AlertEvent alert = rule.evaluate(vehicle.getVehicleId(), location);

        assertNotNull(alert);
        assertEquals("SPEED_100", alert.getRuleKey());
        assertEquals("VEH-001", alert.getVehicleId());
        assertEquals(AlertSeverity.WARNING, alert.getSeverity());
        assertTrue(alert.getMessage().contains("120.0"));
        assertTrue(alert.getMessage().contains("100.0")); // threshold
    }

    @Test
    void testEvaluateCriticalAlertWhenFarAboveThreshold() {
        // Threshold is 100, 1.5x threshold is 150
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 160.0f, testTime);

        AlertEvent alert = rule.evaluate(vehicle.getVehicleId(), location);

        assertNotNull(alert);
        assertEquals(AlertSeverity.CRITICAL, alert.getSeverity());
        assertTrue(alert.getMessage().contains("160.0"));
    }

    @Test
    void testEvaluateRuleDisabled() {
        rule.setEnabled(false);
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 120.0f, testTime);

        AlertEvent alert = rule.evaluate(vehicle.getVehicleId(), location);

        assertNull(alert);
    }

    @Test
    void testEvaluateZeroSpeed() {
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 0.0f, testTime);

        AlertEvent alert = rule.evaluate(vehicle.getVehicleId(), location);

        assertNull(alert);
    }

    @Test
    void testRuleConfiguration() {
        MaxSpeedRule customRule = new MaxSpeedRule("SPEED_50", 50.0f);

        assertEquals("SPEED_50", customRule.getRuleKey());
        assertEquals(50.0f, customRule.getThresholdSpeed(), 0.001);

        // Test enabling/disabling
        customRule.setEnabled(false);
        assertFalse(customRule.isEnabled());

        customRule.setEnabled(true);
        assertTrue(customRule.isEnabled());
    }

    @Test
    void testMultipleVehicles() {
        Vehicle vehicle2 = new Vehicle("VEH-002");
        LocationPoint speedingLocation = new LocationPoint(40.7128, -74.0060, 120.0f, testTime);

        AlertEvent alert1 = rule.evaluate(vehicle.getVehicleId(), speedingLocation);
        AlertEvent alert2 = rule.evaluate(vehicle2.getVehicleId(), speedingLocation);

        assertNotNull(alert1);
        assertNotNull(alert2);
        assertEquals("VEH-001", alert1.getVehicleId());
        assertEquals("VEH-002", alert2.getVehicleId());
        assertNotEquals(alert1, alert2);
    }

    @Test
    void testAlertMessageFormat() {
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 120.5f, testTime);

        AlertEvent alert = rule.evaluate(vehicle.getVehicleId(), location);

        assertNotNull(alert);
        String message = alert.getMessage();
        assertTrue(message.contains("Vehicle VEH-001"));
        assertTrue(message.contains("exceeded speed limit of 100.0 km/h"));
        assertTrue(message.contains("Current speed: 120.5 km/h"));
    }

    @Test
    void testSeverityLevels() {
        // Just below critical threshold (100 * 1.5 = 150)
        LocationPoint warningLocation = new LocationPoint(40.7128, -74.0060, 149.9f, testTime);
        AlertEvent warningAlert = rule.evaluate(vehicle.getVehicleId(), warningLocation);
        assertEquals(AlertSeverity.WARNING, warningAlert.getSeverity());

        // At critical threshold
        LocationPoint criticalLocation = new LocationPoint(40.7128, -74.0060, 190.0f, testTime);
        AlertEvent criticalAlert = rule.evaluate(vehicle.getVehicleId(), criticalLocation);
        assertEquals(AlertSeverity.CRITICAL, criticalAlert.getSeverity());

        // Above critical threshold
        LocationPoint farCriticalLocation = new LocationPoint(40.7128, -74.0060, 200.0f, testTime);
        AlertEvent farCriticalAlert = rule.evaluate(vehicle.getVehicleId(), farCriticalLocation);
        assertEquals(AlertSeverity.CRITICAL, farCriticalAlert.getSeverity());
    }
}
