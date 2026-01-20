package com.jjenus.tracker.core.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.core.domain.entity.AlertRule;
import com.jjenus.tracker.core.domain.enums.AlertRuleType;
import com.jjenus.tracker.core.infrastructure.repository.AlertRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RuleManagementService {
    
    private final AlertRuleRepository ruleRepository;
    private final ObjectMapper objectMapper;
    
    public RuleManagementService(AlertRuleRepository ruleRepository,
                                ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public AlertRule createRule(AlertRule rule) {
        if (ruleRepository.existsByRuleKey(rule.getRuleKey())) {
            throw new IllegalArgumentException("Rule key already exists: " + rule.getRuleKey());
        }
        
        validateRuleParameters(rule);
        
        return ruleRepository.save(rule);
    }
    
    @Transactional
    public AlertRule updateRule(Long ruleId, AlertRule updates) {
        AlertRule rule = ruleRepository.findById(ruleId)
            .orElseThrow(() -> new IllegalArgumentException("Rule not found"));
        
        if (updates.getRuleName() != null) rule.setRuleName(updates.getRuleName());
        if (updates.getIsEnabled() != null) rule.setIsEnabled(updates.getIsEnabled());
        if (updates.getPriority() != null) rule.setPriority(updates.getPriority());
        if (updates.getParameters() != null) {
            validateRuleParameters(rule); // Validate before updating
            rule.setParameters(updates.getParameters());
        }
        if (updates.getConditions() != null) rule.setConditions(updates.getConditions());
        if (updates.getActions() != null) rule.setActions(updates.getActions());
        if (updates.getNotificationChannels() != null) {
            rule.setNotificationChannels(updates.getNotificationChannels());
        }
        if (updates.getCooldownMinutes() != null) {
            rule.setCooldownMinutes(updates.getCooldownMinutes());
        }
        
        return ruleRepository.save(rule);
    }
    
    @Transactional
    public void toggleRule(Long ruleId, boolean enabled) {
        AlertRule rule = ruleRepository.findById(ruleId)
            .orElseThrow(() -> new IllegalArgumentException("Rule not found"));
        
        rule.setIsEnabled(enabled);
        ruleRepository.save(rule);
    }
    
    @Transactional
    public void deleteRule(Long ruleId) {
        if (!ruleRepository.existsById(ruleId)) {
            throw new IllegalArgumentException("Rule not found");
        }
        
        ruleRepository.deleteById(ruleId);
    }
    
    @Transactional(readOnly = true)
    public List<AlertRule> getAllRules() {
        return ruleRepository.findAll();
    }
    
    @Transactional(readOnly = true)
    public List<AlertRule> getActiveRules() {
        return ruleRepository.findByIsEnabled(true);
    }
    
    @Transactional(readOnly = true)
    public List<AlertRule> getRulesByType(AlertRuleType ruleType) {
        return ruleRepository.findByRuleType(ruleType);
    }
    
    @Transactional(readOnly = true)
    public Optional<AlertRule> getRuleByKey(String ruleKey) {
        return ruleRepository.findByRuleKey(ruleKey);
    }
    
    private void validateRuleParameters(AlertRule rule) {
        try {
            Map<String, Object> params = objectMapper.readValue(
                rule.getParameters(), 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
            
            // Validate based on rule type
            switch (rule.getRuleType()) {
                case SPEED:
                    if (!params.containsKey("speedLimit")) {
                        throw new IllegalArgumentException("Speed rule requires speedLimit parameter");
                    }
                    break;
                    
                case TIME:
                    if (!params.containsKey("maxIdleMinutes")) {
                        throw new IllegalArgumentException("Time rule requires maxIdleMinutes parameter");
                    }
                    break;
                    
                case GEOFENCE:
                    if (!params.containsKey("geofenceId")) {
                        throw new IllegalArgumentException("Geofence rule requires geofenceId parameter");
                    }
                    break;
            }
            
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid rule parameters: " + e.getMessage());
        }
    }
    
    // Predefined rule templates
    public AlertRule createOverspeedRule(float speedLimit, float buffer) {
        AlertRule rule = new AlertRule();
        rule.setRuleKey("OVERSPEED_" + (int) speedLimit);
        rule.setRuleName("Overspeed Alert - " + speedLimit + " km/h");
        rule.setRuleType(AlertRuleType.SPEED);
        rule.setPriority(1);
        
        Map<String, Object> params = Map.of(
            "speedLimit", speedLimit,
            "buffer", buffer,
            "severity", "CRITICAL"
        );
        
        Map<String, Object> actions = Map.of(
            "sendNotification", true,
            "executeCommand", false
        );
        
        Map<String, Object> notifications = Map.of(
            "channels", List.of("SMS", "EMAIL", "DASHBOARD")
        );
        
        try {
            rule.setParameters(objectMapper.writeValueAsString(params));
            rule.setActions(objectMapper.writeValueAsString(actions));
            rule.setNotificationChannels(objectMapper.writeValueAsString(notifications));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create rule", e);
        }
        
        return rule;
    }
    
    public AlertRule createIdleTimeoutRule(int maxIdleMinutes) {
        AlertRule rule = new AlertRule();
        rule.setRuleKey("IDLE_TIMEOUT_" + maxIdleMinutes);
        rule.setRuleName("Idle Timeout - " + maxIdleMinutes + " minutes");
        rule.setRuleType(AlertRuleType.TIME);
        rule.setPriority(3);
        
        Map<String, Object> params = Map.of(
            "maxIdleMinutes", maxIdleMinutes,
            "severity", "WARNING"
        );
        
        try {
            rule.setParameters(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            throw new RuntimeException("Failed to create rule", e);
        }
        
        return rule;
    }
}