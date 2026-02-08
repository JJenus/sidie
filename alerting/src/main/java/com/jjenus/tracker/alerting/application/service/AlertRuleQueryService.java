package com.jjenus.tracker.alerting.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.api.dto.AlertRuleResponse;
import com.jjenus.tracker.alerting.api.dto.PagedResponse;
import com.jjenus.tracker.alerting.api.dto.SearchRequest;
import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.domain.enums.AlertRuleType;
import com.jjenus.tracker.alerting.exception.AlertException;
import com.jjenus.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AlertRuleQueryService {

    private static final Logger logger = LoggerFactory.getLogger(AlertRuleQueryService.class);

    private final AlertRuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    public AlertRuleQueryService(AlertRuleRepository ruleRepository, ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "alertRules", key = "'all'")
    public List<AlertRuleResponse> getAllRules() {
        return ruleRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "alertRulesPaged", key = "'search_' + #searchRequest.hashCode()")
    public PagedResponse<AlertRuleResponse> getAllRulesPaged(SearchRequest searchRequest) {
        Pageable pageable = createPageable(searchRequest);
        Page<AlertRule> page = ruleRepository.searchAlertRules(
                searchRequest.getSearch(),
                searchRequest.getRuleType(),
                searchRequest.getEnabled(),
                pageable);

        return new PagedResponse<>(page.map(this::toResponse));
    }

    @Cacheable(value = "alertRules", key = "'enabled'")
    public List<AlertRuleResponse> getEnabledRules() {
        return ruleRepository.findByIsEnabled(true).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "alertRulesPaged", key = "'enabled_' + #searchRequest.hashCode()")
    public PagedResponse<AlertRuleResponse> getEnabledRulesPaged(SearchRequest searchRequest) {
        searchRequest.setEnabled(true);
        return getAllRulesPaged(searchRequest);
    }

    @Cacheable(value = "alertRules", key = "'rule_' + #ruleKey")
    public AlertRuleResponse getRuleByKey(String ruleKey) {
        AlertRule rule = ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));
        return toResponse(rule);
    }

    @Cacheable(value = "alertRules", key = "'rule_' + #ruleKey")
    public AlertRule getRuleEntityByKey(String ruleKey) {
        return ruleRepository.findByRuleKey(ruleKey)
                .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));
    }

    @Cacheable(value = "alertRules", key = "'vehicle_' + #vehicleId")
    public List<AlertRule> getActiveRulesForVehicle(String vehicleId) {
        return ruleRepository.findActiveRulesForVehicle(vehicleId);
    }

    @Cacheable(value = "alertRules", key = "'vehiclesWithRules'")
    public Set<String> getVehiclesWithActiveRules() {
        return ruleRepository.findVehiclesWithActiveRules();
    }

    @Cacheable(value = "vehicleRules", key = "'rulesByVehicle_' + #vehicleIds.hashCode()")
    public Map<String, List<AlertRuleResponse>> getRulesByVehicleIds(Set<String> vehicleIds) {
        Map<String, List<AlertRuleResponse>> result = new HashMap<>();

        for (String vehicleId : vehicleIds) {
            List<AlertRule> vehicleRules = ruleRepository.findActiveRulesForVehicle(vehicleId);
            result.put(vehicleId, vehicleRules.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList()));
        }

        return result;
    }

    public boolean existsByRuleKey(String ruleKey) {
        return ruleRepository.existsByRuleKey(ruleKey);
    }

    private Pageable createPageable(SearchRequest searchRequest) {
        Sort sort = Sort.by(searchRequest.getSortDirection(), searchRequest.getSortBy());
        return PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), sort);
    }

    public AlertRuleResponse toResponse(AlertRule rule) {
        AlertRuleResponse response = new AlertRuleResponse();
        response.setRuleKey(rule.getRuleKey());
        response.setRuleName(rule.getRuleName());
        response.setRuleType(rule.getRuleType());
        response.setParameters(rule.getParameters());
        response.setPriority(rule.getPriority() != null ? rule.getPriority() : 5);
        response.setEnabled(Boolean.TRUE.equals(rule.isEnabled()));
        response.setCreatedAt(rule.getCreatedAt());
        response.setUpdatedAt(rule.getUpdatedAt());
        return response;
    }

    // Parameter parsing utilities
    public Map<String, Object> parseParameters(String parametersJson) {
        try {
            if (!StringUtils.hasText(parametersJson)) {
                return new HashMap<>();
            }
            return objectMapper.readValue(parametersJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid parameters JSON format: " + e.getMessage(), e);
        }
    }

    public AlertRuleType parseRuleType(String ruleTypeStr) {
        try {
            return AlertRuleType.valueOf(ruleTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid rule type: " + ruleTypeStr +
                    ". Valid values: " + Arrays.toString(AlertRuleType.values()));
        }
    }
}
