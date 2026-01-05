package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.alerting.domain.IAlertRule;
import com.jjenus.tracker.alerting.domain.AlertEvent;
import com.jjenus.tracker.alerting.domain.AlertSeverity;
import com.jjenus.tracker.alerting.domain.MaxSpeedRule;
import com.jjenus.tracker.core.domain.Vehicle;
import com.jjenus.tracker.core.domain.LocationPoint;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
import com.jjenus.tracker.alerting.exception.AlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AlertingEngineTest {

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private AlertRuleEvaluationService evaluationService;

    @Captor
    private ArgumentCaptor<AlertEvent> alertEventCaptor;

    private AlertingEngine alertingEngine;
    private Vehicle testVehicle;
    private LocationPoint testLocation;
    private Instant testTime;

    @BeforeEach
    void setUp() {
        alertingEngine = new AlertingEngine(eventPublisher, evaluationService);
        testTime = Instant.now();
        testVehicle = new Vehicle("VEH-001");
        testLocation = new LocationPoint(40.7128, -74.0060, 30.0f, testTime);
    }

    @Test
    void testProcessVehicleUpdateWithNoRules() {
        alertingEngine.processVehicleUpdate(testVehicle, testLocation);

        verifyNoInteractions(evaluationService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void testProcessVehicleUpdateWithSingleRuleNoAlert() {
        IAlertRule mockRule = mock(IAlertRule.class);
        when(mockRule.getRuleName()).thenReturn("Test Rule");
        when(mockRule.isEnabled()).thenReturn(true);
        
        when(evaluationService.validateRuleConfiguration(mockRule)).thenReturn(true);
        when(evaluationService.evaluateRule(mockRule, testVehicle, testLocation)).thenReturn(null);

        alertingEngine.registerRule(mockRule);
        alertingEngine.processVehicleUpdate(testVehicle, testLocation);

        verify(evaluationService).evaluateRule(mockRule, testVehicle, testLocation);
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void testProcessVehicleUpdateWithSingleRuleAlert() {
        IAlertRule mockRule = mock(IAlertRule.class);
        when(mockRule.getRuleName()).thenReturn("Test Rule");
        when(mockRule.isEnabled()).thenReturn(true);
        
        when(evaluationService.validateRuleConfiguration(mockRule)).thenReturn(true);

        AlertEvent alertEvent = new AlertEvent("TEST_RULE", "VEH-001",
                "Test alert", AlertSeverity.WARNING, testLocation);

        when(evaluationService.evaluateRule(mockRule, testVehicle, testLocation)).thenReturn(alertEvent);

        alertingEngine.registerRule(mockRule);
        alertingEngine.processVehicleUpdate(testVehicle, testLocation);

        verify(evaluationService).evaluateRule(mockRule, testVehicle, testLocation);
        verify(eventPublisher).publish(alertEventCaptor.capture());

        AlertEvent publishedAlert = alertEventCaptor.getValue();
        assertEquals(alertEvent, publishedAlert);
        assertEquals("VEH-001", publishedAlert.getVehicleId());
    }

    @Test
    void testProcessVehicleUpdateWithMultipleRules() {
        IAlertRule rule1 = mock(IAlertRule.class);
        IAlertRule rule2 = mock(IAlertRule.class);

        when(rule1.getRuleKey()).thenReturn("RULE_1");
        when(rule1.getRuleName()).thenReturn("Rule 1");
        when(rule1.isEnabled()).thenReturn(true);
        when(rule1.getPriority()).thenReturn(2);
        when(evaluationService.validateRuleConfiguration(rule1)).thenReturn(true);

        when(rule2.getRuleKey()).thenReturn("RULE_2");
        when(rule2.getRuleName()).thenReturn("Rule 2");
        when(rule2.isEnabled()).thenReturn(true);
        when(rule2.getPriority()).thenReturn(1);
        when(evaluationService.validateRuleConfiguration(rule2)).thenReturn(true);

        AlertEvent alert1 = new AlertEvent("RULE_1", "VEH-001",
                "Alert 1", AlertSeverity.WARNING, testLocation);
        AlertEvent alert2 = new AlertEvent("RULE_2", "VEH-001",
                "Alert 2", AlertSeverity.WARNING, testLocation);

        when(evaluationService.evaluateRule(rule1, testVehicle, testLocation)).thenReturn(alert1);
        when(evaluationService.evaluateRule(rule2, testVehicle, testLocation)).thenReturn(alert2);

        alertingEngine.registerRule(rule1);
        alertingEngine.registerRule(rule2);
        alertingEngine.processVehicleUpdate(testVehicle, testLocation);

        verify(evaluationService).evaluateRule(rule1, testVehicle, testLocation);
        verify(evaluationService).evaluateRule(rule2, testVehicle, testLocation);
        verify(eventPublisher, times(2)).publish(any(AlertEvent.class));
    }

    @Test
    void testProcessVehicleUpdateWithDisabledRule() {
        IAlertRule disabledRule = mock(IAlertRule.class);
//        when(disabledRule.getRuleKey()).thenReturn("DISABLED_RULE");
        when(disabledRule.getRuleName()).thenReturn("Disabled Rule");
        when(disabledRule.isEnabled()).thenReturn(false);
        when(evaluationService.validateRuleConfiguration(disabledRule)).thenReturn(true);

        alertingEngine.registerRule(disabledRule);
        alertingEngine.processVehicleUpdate(testVehicle, testLocation);

        verify(evaluationService, never()).evaluateRule(any(), any(), any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void testProcessVehicleUpdateRulePriorityOrder() {
        IAlertRule lowPriorityRule = mock(IAlertRule.class);
        IAlertRule highPriorityRule = mock(IAlertRule.class);

        when(lowPriorityRule.getRuleKey()).thenReturn("LOW");
        when(lowPriorityRule.getRuleName()).thenReturn("Low Priority Rule");
        when(lowPriorityRule.isEnabled()).thenReturn(true);
        when(lowPriorityRule.getPriority()).thenReturn(1);
        when(evaluationService.validateRuleConfiguration(lowPriorityRule)).thenReturn(true);

        when(highPriorityRule.getRuleKey()).thenReturn("HIGH");
        when(highPriorityRule.getRuleName()).thenReturn("High Priority Rule");
        when(highPriorityRule.isEnabled()).thenReturn(true);
        when(highPriorityRule.getPriority()).thenReturn(3);
        when(evaluationService.validateRuleConfiguration(highPriorityRule)).thenReturn(true);

        // Both rules return alerts
        when(evaluationService.evaluateRule(any(), any(), any()))
                .thenReturn(new AlertEvent("TEST", "VEH-001", "Alert", AlertSeverity.WARNING, testLocation));

        alertingEngine.registerRule(lowPriorityRule);
        alertingEngine.registerRule(highPriorityRule);
        alertingEngine.processVehicleUpdate(testVehicle, testLocation);

        var orderVerifier = inOrder(evaluationService);
        orderVerifier.verify(evaluationService).evaluateRule(highPriorityRule, testVehicle, testLocation);
        orderVerifier.verify(evaluationService).evaluateRule(lowPriorityRule, testVehicle, testLocation);
    }

    @Test
    void testProcessVehicleUpdateWithNullVehicle() {
        com.jjenus.tracker.shared.exception.ValidationException exception =
                assertThrows(com.jjenus.tracker.shared.exception.ValidationException.class,
                        () -> alertingEngine.processVehicleUpdate(null, testLocation));

        assertEquals("ALERT_INVALID_INPUT", exception.getErrorCode());
    }

    @Test
    void testProcessVehicleUpdateWithNullLocation() {
        com.jjenus.tracker.shared.exception.ValidationException exception =
                assertThrows(com.jjenus.tracker.shared.exception.ValidationException.class,
                        () -> alertingEngine.processVehicleUpdate(testVehicle, null));

        assertEquals("ALERT_INVALID_INPUT", exception.getErrorCode());
    }

    @Test
    void testRegisterRule() {
        IAlertRule mockRule = mock(IAlertRule.class);
        when(mockRule.getRuleName()).thenReturn("Test Rule");
        when(evaluationService.validateRuleConfiguration(mockRule)).thenReturn(true);

        alertingEngine.registerRule(mockRule);

        List<IAlertRule> activeRules = alertingEngine.getActiveRules();
        assertEquals(1, activeRules.size());
        assertEquals(mockRule, activeRules.get(0));
    }

    @Test
    void testRegisterRuleNull() {
        com.jjenus.tracker.shared.exception.ValidationException exception =
                assertThrows(com.jjenus.tracker.shared.exception.ValidationException.class,
                        () -> alertingEngine.registerRule(null));

        assertEquals("ALERT_RULE_NULL", exception.getErrorCode());
    }

    @Test
    void testRegisterRuleAlreadyExists() {
        IAlertRule mockRule = mock(IAlertRule.class);
        when(mockRule.getRuleName()).thenReturn("Test Rule");
        when(mockRule.getRuleKey()).thenReturn("Test_Rule");
        when(evaluationService.validateRuleConfiguration(mockRule)).thenReturn(true);

        alertingEngine.registerRule(mockRule);

        AlertException exception = assertThrows(AlertException.class,
                () -> alertingEngine.registerRule(mockRule));

        assertEquals("ALERT_RULE_EXISTS", exception.getErrorCode());
    }

    @Test
    void testRegisterRuleInvalidConfiguration() {
        IAlertRule mockRule = mock(IAlertRule.class);
        when(mockRule.getRuleKey()).thenReturn("");
        when(evaluationService.validateRuleConfiguration(mockRule)).thenReturn(false);

        AlertException exception = assertThrows(AlertException.class,
                () -> alertingEngine.registerRule(mockRule));

        assertEquals("ALERT_INVALID_CONFIG", exception.getErrorCode());
    }

    @Test
    void testUnregisterRule() {
        IAlertRule mockRule = mock(IAlertRule.class);
        when(mockRule.getRuleName()).thenReturn("Test Rule");
        when(mockRule.getRuleKey()).thenReturn("TEST_RULE");
        when(evaluationService.validateRuleConfiguration(mockRule)).thenReturn(true);

        alertingEngine.registerRule(mockRule);
        assertEquals(1, alertingEngine.getActiveRules().size());

        alertingEngine.unregisterRule("TEST_RULE");
        assertEquals(0, alertingEngine.getActiveRules().size());
    }

    @Test
    void testUnregisterRuleNotFound() {
        AlertException exception = assertThrows(AlertException.class,
                () -> alertingEngine.unregisterRule("NONEXISTENT"));

        assertEquals("ALERT_RULE_NOT_FOUND", exception.getErrorCode());
    }

    @Test
    void testGetActiveRulesReturnsCopy() {
        IAlertRule mockRule = mock(IAlertRule.class);
        when(mockRule.getRuleName()).thenReturn("Test Rule");
        when(evaluationService.validateRuleConfiguration(mockRule)).thenReturn(true);

        alertingEngine.registerRule(mockRule);

        List<IAlertRule> rules = alertingEngine.getActiveRules();
        assertEquals(1, rules.size());

        // Modifying the returned list should not affect the engine
        assertThrows(UnsupportedOperationException.class, () -> rules.add(mock(IAlertRule.class)));
    }

    @Test
    void testClearRules() {
        IAlertRule mockRule1 = mock(IAlertRule.class);
        IAlertRule mockRule2 = mock(IAlertRule.class);

        when(mockRule1.getRuleKey()).thenReturn("RULE_1");
        when(mockRule1.getRuleName()).thenReturn("Rule 1");
        when(evaluationService.validateRuleConfiguration(mockRule1)).thenReturn(true);

        when(mockRule2.getRuleKey()).thenReturn("RULE_2");
        when(mockRule2.getRuleName()).thenReturn("Rule 2");
        when(evaluationService.validateRuleConfiguration(mockRule2)).thenReturn(true);

        alertingEngine.registerRule(mockRule1);
        alertingEngine.registerRule(mockRule2);

        assertEquals(2, alertingEngine.getActiveRules().size());

        alertingEngine.clearRules();

        assertEquals(0, alertingEngine.getActiveRules().size());
    }

    @Test
    void testRuleEvaluationExceptionHandling() {
        IAlertRule mockRule = mock(IAlertRule.class);
        when(mockRule.getRuleKey()).thenReturn("ERROR_RULE");
        when(mockRule.getRuleName()).thenReturn("Error Rule");
        when(mockRule.isEnabled()).thenReturn(true);
        
        when(evaluationService.validateRuleConfiguration(mockRule)).thenReturn(true);

        when(evaluationService.evaluateRule(mockRule, testVehicle, testLocation))
                .thenThrow(new AlertException("RULE_ERROR", "Evaluation failed"));

        alertingEngine.registerRule(mockRule);

        AlertException exception = assertThrows(AlertException.class,
                () -> alertingEngine.processVehicleUpdate(testVehicle, testLocation));

        assertEquals("RULE_ERROR", exception.getErrorCode());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void testUnexpectedExceptionHandling() {
        IAlertRule mockRule = mock(IAlertRule.class);
        when(mockRule.getRuleKey()).thenReturn("ERROR_RULE");
        when(mockRule.getRuleName()).thenReturn("Error Rule");
        when(mockRule.isEnabled()).thenReturn(true);
        
        when(evaluationService.validateRuleConfiguration(mockRule)).thenReturn(true);

        when(evaluationService.evaluateRule(mockRule, testVehicle, testLocation))
                .thenThrow(new RuntimeException("Unexpected error"));

        alertingEngine.registerRule(mockRule);

        AlertException exception = assertThrows(AlertException.class,
                () -> alertingEngine.processVehicleUpdate(testVehicle, testLocation));

        assertEquals("ALERT_EVALUATION_ERROR", exception.getErrorCode());
        assertTrue(exception.getMessage().contains("ERROR_RULE"));
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void testRealRuleIntegration() {
        // Test with actual rule implementation
        MaxSpeedRule speedRule = new MaxSpeedRule("SPEED_100", 100.0f);

        // Use real evaluation service
        AlertRuleEvaluationService realService = new AlertRuleEvaluationService();
        AlertingEngine realEngine = new AlertingEngine(eventPublisher, realService);

        realEngine.registerRule(speedRule);

        // Test with speeding vehicle
        LocationPoint speedingLocation = new LocationPoint(40.7128, -74.0060, 120.0f, testTime);
        realEngine.processVehicleUpdate(testVehicle, speedingLocation);

        verify(eventPublisher).publish(any(AlertEvent.class));
    }
}