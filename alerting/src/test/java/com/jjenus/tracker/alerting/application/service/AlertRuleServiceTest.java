package com.jjenus.tracker.alerting.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.api.dto.*;
import com.jjenus.tracker.alerting.domain.AlertRuleTestBuilder;
import com.jjenus.tracker.alerting.domain.GeofenceTestBuilder;
import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.domain.entity.Geofence;
import com.jjenus.tracker.alerting.domain.enums.AlertRuleType;
import com.jjenus.tracker.alerting.exception.AlertException;
import com.jjenus.tracker.alerting.infrastructure.cache.AlertRuleCacheService;
import com.jjenus.tracker.alerting.infrastructure.cache.RedisKeyGenerator;
import com.jjenus.tracker.alerting.infrastructure.cache.VehicleRuleCacheService;
import com.jjenus.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    @Mock
    private AlertRuleRepository ruleRepository;

    @Mock
    private AlertRuleCacheService ruleCacheService;

    @Mock
    private VehicleRuleCacheService vehicleRuleCacheService;

    @Mock
    private GeofenceRuleValidator geofenceRuleValidator;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisKeyGenerator keyGenerator;

    @InjectMocks
    private AlertRuleService alertRuleService;

    private AlertRule testRule;
    private Geofence testGeofence;

    @BeforeEach
    void setUp() {
        testRule = AlertRuleTestBuilder.defaultRule()
            .ruleKey("test-rule")
            .vehicleId("vehicle-001")
            .build();

        testGeofence = GeofenceTestBuilder.defaultGeofence()
            .geofenceId(1L)
            .vehicleId("vehicle-001")
            .build();
    }

    @Test
    void createRule_validRequest_returnsCreatedRule() throws JsonProcessingException {
        // given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setRuleKey("new-rule");
        request.setRuleName("New Alert Rule");
        request.setRuleType("SPEED");
        request.setParameters("{\"speedLimit\":80}");
        request.setPriority(1);
        request.setEnabled(true);

        when(ruleRepository.existsByRuleKey("new-rule")).thenReturn(false);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);
        when(objectMapper.readValue(anyString(), (Class<Object>) any())).thenReturn(Map.of("speedLimit", 80.0f));

        // when
        AlertRuleResponse result = alertRuleService.createRule(request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getRuleKey()).isEqualTo("test-rule");
        verify(ruleRepository).save(any(AlertRule.class));
        verify(ruleCacheService).cacheRule(any(AlertRule.class));
    }

    @Test
    void createRule_duplicateRuleKey_throwsException() {
        // given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setRuleKey("existing-rule");

        when(ruleRepository.existsByRuleKey("existing-rule")).thenReturn(true);

        // when & then
        assertThatThrownBy(() -> alertRuleService.createRule(request))
            .isInstanceOf(AlertException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void createRule_invalidRuleType_throwsException() {
        // given
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setRuleKey("new-rule");
        request.setRuleName("New Rule");
        request.setRuleType("INVALID_TYPE");
        request.setParameters("{}");

        when(ruleRepository.existsByRuleKey("new-rule")).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> alertRuleService.createRule(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid rule type");
    }

    @Test
    void getRuleByKey_existingRule_returnsRule() {
        // given
        when(ruleCacheService.getRuleByKey("test-rule")).thenReturn(Optional.empty());
        when(ruleRepository.findByRuleKey("test-rule")).thenReturn(Optional.of(testRule));

        // when
        AlertRuleResponse result = alertRuleService.getRuleByKey("test-rule");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getRuleKey()).isEqualTo("test-rule");
    }

    @Test
    void getRuleByKey_nonExistentRule_throwsException() {
        // given
        when(ruleCacheService.getRuleByKey("non-existent")).thenReturn(Optional.empty());
        when(ruleRepository.findByRuleKey("non-existent")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> alertRuleService.getRuleByKey("non-existent"))
            .isInstanceOf(AlertException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void getAllRulesPaged_returnsPagedResults() {
        // given
        PageRequest pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
        Page<AlertRule> page = new PageImpl<>(List.of(testRule), pageable, 1);

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setPage(0);
        searchRequest.setSize(20);
        searchRequest.setSortBy("createdAt");
        searchRequest.setSortDirection(Sort.Direction.DESC);

        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(keyGenerator.getPaginatedRulesKey(anyInt(), anyInt(), anyString(), anyString(), any(), any(), any()))
            .thenReturn("cache-key");
        when(ruleRepository.searchAlertRules(any(), any(), any(), any())).thenReturn(page);

        // when
        PagedResponse<AlertRuleResponse> result = alertRuleService.getAllRulesPaged(searchRequest);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getPageNumber()).isEqualTo(0);
    }

    @Test
    void enableRule_existingRule_enablesIt() {
        // given
        testRule.setIsEnabled(false);
        when(ruleRepository.findByRuleKey("test-rule")).thenReturn(Optional.of(testRule));

        // when
        alertRuleService.enableRule("test-rule");

        // then
        assertThat(testRule.isEnabled()).isTrue();
        verify(ruleRepository).save(testRule);
        verify(ruleCacheService).cacheRule(testRule);
        verify(vehicleRuleCacheService).invalidateVehicleRules("vehicle-001");
    }

    @Test
    void enableRule_alreadyEnabled_noAction() {
        // given
        testRule.setIsEnabled(true);
        when(ruleRepository.findByRuleKey("test-rule")).thenReturn(Optional.of(testRule));

        // when
        alertRuleService.enableRule("test-rule");

        // then
        verify(ruleRepository, never()).save(any());
    }

    @Test
    void disableRule_existingRule_disablesIt() {
        // given
        testRule.setIsEnabled(true);
        when(ruleRepository.findByRuleKey("test-rule")).thenReturn(Optional.of(testRule));

        // when
        alertRuleService.disableRule("test-rule");

        // then
        assertThat(testRule.isEnabled()).isFalse();
        verify(ruleRepository).save(testRule);
        verify(ruleCacheService).evictRule("test-rule");
        verify(vehicleRuleCacheService).invalidateVehicleRules("vehicle-001");
    }

    @Test
    void deleteRule_existingRule_deletesIt() {
        // given
        when(ruleRepository.findByRuleKey("test-rule")).thenReturn(Optional.of(testRule));
        doNothing().when(ruleRepository).deleteByRuleKey("test-rule");

        // when
        alertRuleService.deleteRule("test-rule");

        // then
        verify(ruleRepository).deleteByRuleKey("test-rule");
        verify(ruleCacheService).evictRule("test-rule");
        verify(vehicleRuleCacheService).invalidateVehicleRules("vehicle-001");
    }

    @Test
    void deleteRule_nonExistentRule_throwsException() {
        // given
        when(ruleRepository.findByRuleKey("non-existent")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> alertRuleService.deleteRule("non-existent"))
            .isInstanceOf(AlertException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void createOverspeedRule_validRequest_returnsCreatedRule() {
        // given
        OverspeedRuleTemplateRequest request = new OverspeedRuleTemplateRequest();
        request.setRuleKey("overspeed-rule");
        request.setRuleName("Overspeed Alert");
        request.setSpeedLimit(80.0f);
        request.setBuffer(5.0f);
        request.setVehicleIds(Set.of("vehicle-001"));
        request.setPriority(1);
        request.setEnabled(true);

        when(ruleRepository.existsByRuleKey("overspeed-rule")).thenReturn(false);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);

        // when
        AlertRuleResponse result = alertRuleService.createOverspeedRule(request);

        // then
        assertThat(result).isNotNull();
        verify(ruleRepository).save(any(AlertRule.class));
    }

    @Test
    void createOverspeedRule_invalidSpeedLimit_throwsException() {
        // given
        OverspeedRuleTemplateRequest request = new OverspeedRuleTemplateRequest();
        request.setSpeedLimit(0.0f);
        request.setVehicleIds(Set.of("vehicle-001"));

        // when & then
        assertThatThrownBy(() -> alertRuleService.createOverspeedRule(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Speed limit must be positive");
    }

    @Test
    void createOverspeedRule_noVehicles_throwsException() {
        // given
        OverspeedRuleTemplateRequest request = new OverspeedRuleTemplateRequest();
        request.setSpeedLimit(80.0f);
        request.setVehicleIds(Collections.emptySet());

        // when & then
        assertThatThrownBy(() -> alertRuleService.createOverspeedRule(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one vehicle ID is required");
    }

    @Test
    void createIdleTimeoutRule_validRequest_returnsCreatedRule() {
        // given
        IdleTimeoutRuleTemplateRequest request = new IdleTimeoutRuleTemplateRequest();
        request.setRuleKey("idle-rule");
        request.setRuleName("Idle Timeout");
        request.setMaxIdleMinutes(30);
        request.setVehicleIds(Set.of("vehicle-001"));
        request.setPriority(2);
        request.setEnabled(true);

        when(ruleRepository.existsByRuleKey("idle-rule")).thenReturn(false);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);

        // when
        AlertRuleResponse result = alertRuleService.createIdleTimeoutRule(request);

        // then
        assertThat(result).isNotNull();
        verify(ruleRepository).save(any(AlertRule.class));
    }

    @Test
    void createGeofenceRule_validRequest_returnsCreatedRule() {
        // given
        GeofenceRuleTemplateRequest request = new GeofenceRuleTemplateRequest();
        request.setRuleKey("geofence-rule");
        request.setRuleName("Geofence Alert");
        request.setGeofenceId("1");
        request.setAction(GeofenceRuleTemplateRequest.GeofenceAction.BOTH);
        request.setVehicleIds(Set.of("vehicle-001"));
        request.setPriority(3);
        request.setEnabled(true);

        when(ruleRepository.existsByRuleKey("geofence-rule")).thenReturn(false);
        when(geofenceRuleValidator.getValidatedGeofence("1")).thenReturn(testGeofence);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);

        // when
        AlertRuleResponse result = alertRuleService.createGeofenceRule(request);

        // then
        assertThat(result).isNotNull();
        verify(ruleRepository).save(any(AlertRule.class));
    }

    @Test
    void updateRule_validRequest_updatesRule() {
        // given
        UpdateAlertRuleRequest request = new UpdateAlertRuleRequest();
        request.setRuleName("Updated Name");
        request.setPriority(10);

        when(ruleRepository.findByRuleKey("test-rule")).thenReturn(Optional.of(testRule));
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);

        // when
        AlertRuleResponse result = alertRuleService.updateRule("test-rule", request);

        // then
        assertThat(result).isNotNull();
        assertThat(testRule.getRuleName()).isEqualTo("Updated Name");
        assertThat(testRule.getPriority()).isEqualTo(10);
        verify(ruleRepository).save(testRule);
        verify(ruleCacheService).cacheRule(testRule);
    }

    @Test
    void updateRule_ruleKeyChange_updatesKey() {
        // given
        UpdateAlertRuleRequest request = new UpdateAlertRuleRequest();
        request.setRuleKey("new-key");

        when(ruleRepository.findByRuleKey("test-rule")).thenReturn(Optional.of(testRule));
        when(ruleRepository.existsByRuleKey("new-key")).thenReturn(false);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);

        // when
        AlertRuleResponse result = alertRuleService.updateRule("test-rule", request);

        // then
        assertThat(result).isNotNull();
        assertThat(testRule.getRuleKey()).isEqualTo("new-key");
        verify(ruleCacheService).cacheRule(testRule);
        verify(ruleCacheService).evictRule("test-rule");
    }

    @Test
    void batchCreateRules_multipleRequests_createsRules() throws JsonProcessingException {
        // given
        CreateAlertRuleRequest request1 = new CreateAlertRuleRequest();
        request1.setRuleKey("rule-1");
        request1.setRuleName("Rule 1");
        request1.setRuleType("SPEED");
        request1.setParameters("{}");

        CreateAlertRuleRequest request2 = new CreateAlertRuleRequest();
        request2.setRuleKey("rule-2");
        request2.setRuleName("Rule 2");
        request2.setRuleType("TIME");
        request2.setParameters("{}");

        when(ruleRepository.existsByRuleKey(anyString())).thenReturn(false);
        when(ruleRepository.save(any(AlertRule.class))).thenReturn(testRule);
        when(objectMapper.readValue(anyString(), (Class<Object>) any())).thenReturn(Collections.emptyMap());

        // when
        List<AlertRuleResponse> results = alertRuleService.batchCreateRules(List.of(request1, request2));

        // then
        assertThat(results).hasSize(2);
        verify(ruleRepository, times(2)).save(any(AlertRule.class));
    }

    @Test
    void batchEnableRules_multipleRules_enablesThem() {
        // given
        Set<String> ruleKeys = Set.of("rule-1", "rule-2");
        testRule.setIsEnabled(false);

        when(ruleRepository.findByRuleKey(anyString())).thenReturn(Optional.of(testRule));

        // when
        alertRuleService.batchEnableRules(new HashSet<>(ruleKeys));

        // then
        verify(ruleRepository, times(2)).save(any(AlertRule.class));
        verify(ruleCacheService, times(2)).cacheRule(any(AlertRule.class));
    }
}
