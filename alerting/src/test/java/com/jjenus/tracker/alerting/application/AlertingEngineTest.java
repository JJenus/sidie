package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.alerting.application.service.AlertRuleEvaluationService;
import com.jjenus.tracker.alerting.domain.AlertDetectedEvent;
import com.jjenus.tracker.alerting.domain.AlertRuleTestBuilder;
import com.jjenus.tracker.alerting.domain.IAlertRule;
import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.infrastructure.cache.VehicleRuleCacheService;
import com.jjenus.tracker.shared.domain.LocationPoint;
import com.jjenus.tracker.shared.exception.ValidationException;
import com.jjenus.tracker.shared.pubsub.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertingEngineTest {

    @Mock
    private VehicleRuleCacheService vehicleRuleCacheService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private AlertRuleEvaluationService evaluationService;

    private AlertingEngine alertingEngine;

    @BeforeEach
    void setUp() {
        alertingEngine = new AlertingEngine(eventPublisher, evaluationService, vehicleRuleCacheService);
    }

    @Test
    void processVehicleUpdate_nullVehicleId_throwsException() {
        // given
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.0f, Instant.now());

        // when & then
        assertThatThrownBy(() -> alertingEngine.processVehicleUpdate(null, location))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Vehicle and location cannot be null");
    }

    @Test
    void processVehicleUpdate_nullLocation_throwsException() {
        // when & then
        assertThatThrownBy(() -> alertingEngine.processVehicleUpdate("vehicle-001", null))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Vehicle and location cannot be null");
    }

    @Test
    void processVehicleUpdate_noCachedRules_skipsProcessing() {
        // given
        String vehicleId = "vehicle-001";
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.0f, Instant.now());

        when(vehicleRuleCacheService.hasRulesCached(vehicleId)).thenReturn(false);

        // when
        alertingEngine.processVehicleUpdate(vehicleId, location);

        // then
        verify(vehicleRuleCacheService).hasRulesCached(vehicleId);
        verify(vehicleRuleCacheService, never()).getActiveRulesForVehicle(vehicleId);
        verify(evaluationService, never()).evaluateRule(any(), any(), any());
    }

    @Test
    void processVehicleUpdate_noActiveRules_skipsProcessing() {
        // given
        String vehicleId = "vehicle-001";
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.0f, Instant.now());

        when(vehicleRuleCacheService.hasRulesCached(vehicleId)).thenReturn(true);
        when(vehicleRuleCacheService.hasActiveRules(vehicleId)).thenReturn(false);

        // when
        alertingEngine.processVehicleUpdate(vehicleId, location);

        // then
        verify(vehicleRuleCacheService).hasActiveRules(vehicleId);
        verify(vehicleRuleCacheService, never()).getActiveRulesForVehicle(vehicleId);
    }

    @Test
    void processVehicleUpdate_activeRules_processed() {
        // given
        String vehicleId = "vehicle-001";
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.0f, Instant.now());

        AlertRule rule1 = AlertRuleTestBuilder.overspeedRule()
            .ruleKey("speed-rule")
            .vehicleId(vehicleId)
            .build();

        AlertRule rule2 = AlertRuleTestBuilder.idleTimeoutRule()
            .ruleKey("idle-rule")
            .vehicleId(vehicleId)
            .build();

        AlertDetectedEvent alert = new AlertDetectedEvent(
            "speed-rule",
            vehicleId,
            "Speed exceeded",
            com.jjenus.tracker.alerting.domain.enums.AlertSeverity.WARNING,
            location
        );

        when(vehicleRuleCacheService.hasRulesCached(vehicleId)).thenReturn(true);
        when(vehicleRuleCacheService.hasActiveRules(vehicleId)).thenReturn(true);
        when(vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId))
            .thenReturn(List.of(rule1, rule2));
        when(evaluationService.evaluateRule(any(IAlertRule.class), eq(vehicleId), eq(location)))
            .thenReturn(alert);

        // when
        alertingEngine.processVehicleUpdate(vehicleId, location);

        // then
        verify(vehicleRuleCacheService).getActiveRulesForVehicle(vehicleId);
        verify(evaluationService, times(2)).evaluateRule(any(), eq(vehicleId), eq(location));
        verify(eventPublisher).publish(alert);
    }

    @Test
    void processVehicleUpdate_ruleEvaluationError_continuesProcessing() {
        // given
        String vehicleId = "vehicle-001";
        LocationPoint location = new LocationPoint(40.7128, -74.0060, 60.0f, Instant.now());

        AlertRule rule1 = AlertRuleTestBuilder.overspeedRule()
            .ruleKey("bad-rule")
            .vehicleId(vehicleId)
            .build();

        AlertRule rule2 = AlertRuleTestBuilder.idleTimeoutRule()
            .ruleKey("good-rule")
            .vehicleId(vehicleId)
            .build();

        AlertDetectedEvent alert = new AlertDetectedEvent(
            "good-rule",
            vehicleId,
            "Alert triggered",
            com.jjenus.tracker.alerting.domain.enums.AlertSeverity.INFO,
            location
        );

        when(vehicleRuleCacheService.hasRulesCached(vehicleId)).thenReturn(true);
        when(vehicleRuleCacheService.hasActiveRules(vehicleId)).thenReturn(true);
        when(vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId))
            .thenReturn(List.of(rule1, rule2));
        when(evaluationService.evaluateRule(any(IAlertRule.class), eq(vehicleId), eq(location)))
            .thenThrow(new RuntimeException("Evaluation error"))
            .thenReturn(alert);

        // when
        alertingEngine.processVehicleUpdate(vehicleId, location);

        // then
        verify(evaluationService, times(2)).evaluateRule(any(), eq(vehicleId), eq(location));
        verify(eventPublisher).publish(alert);
    }

    @Test
    void invalidateVehicleCache_callsCacheService() {
        // given
        String vehicleId = "vehicle-001";

        // when
        alertingEngine.invalidateVehicleCache(vehicleId);

        // then
        verify(vehicleRuleCacheService).invalidateVehicleRules(vehicleId);
    }

    @Test
    void invalidateAllCache_callsCacheService() {
        // when
        alertingEngine.invalidateAllCache();

        // then
        verify(vehicleRuleCacheService).invalidateAllVehicleRules();
    }

    @Test
    void refreshVehicleRules_callsCacheService() {
        // given
        String vehicleId = "vehicle-001";
        when(vehicleRuleCacheService.getActiveRulesForVehicle(vehicleId))
            .thenReturn(List.of());

        // when
        alertingEngine.refreshVehicleRules(vehicleId);

        // then
        verify(vehicleRuleCacheService).invalidateVehicleRules(vehicleId);
        verify(vehicleRuleCacheService).getActiveRulesForVehicle(vehicleId);
    }
}
