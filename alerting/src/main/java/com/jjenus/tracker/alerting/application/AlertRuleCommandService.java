package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.alerting.api.dto.*;
import com.jjenus.tracker.alerting.domain.AlertRule;
import com.jjenus.tracker.alerting.domain.AlertRuleRepository;
import com.jjenus.tracker.alerting.exception.AlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class AlertRuleCommandService {
    
    private final AlertRuleRepository ruleRepository;
    
    public AlertRuleCommandService(AlertRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }
    
    public AlertRuleResponse createRule(CreateAlertRuleRequest request) {
        // Check if rule already exists
        if (ruleRepository.existsByKey(request.getRuleKey())) {
            throw AlertException.ruleAlreadyExists(request.getRuleKey());
        }
        
        // Create domain entity
        AlertRule rule = new AlertRule(
            request.getRuleKey(),
            request.getRuleName(),
            request.getRuleType(),
            request.getParameters(),
            request.getPriority(),
            request.isEnabled()
        );
        
        // Save
        AlertRule saved = ruleRepository.save(rule);
        
        return toResponse(saved);
    }
    
    public AlertRuleResponse updateRule(String ruleKey, UpdateAlertRuleRequest request) {
        AlertRule rule = ruleRepository.findByKey(ruleKey)
            .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));
        
        // Update fields
        if (request.getRuleName() != null) {
            // In domain entity, we would have a method like rule.updateName(...)
        }
        if (request.getParameters() != null) {
            // Update parameters
        }
        if (request.getPriority() > 0) {
            // Update priority
        }
        
        AlertRule updated = ruleRepository.save(rule);
        return toResponse(updated);
    }
    
    public void deleteRule(String ruleKey) {
        if (!ruleRepository.existsByKey(ruleKey)) {
            throw AlertException.ruleNotFound(ruleKey);
        }
        ruleRepository.delete(ruleKey);
    }
    
    public void enableRule(String ruleKey) {
        AlertRule rule = ruleRepository.findByKey(ruleKey)
            .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));
        rule.setEnabled(true);
        ruleRepository.save(rule);
    }
    
    public void disableRule(String ruleKey) {
        AlertRule rule = ruleRepository.findByKey(ruleKey)
            .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));
        rule.setEnabled(false);
        ruleRepository.save(rule);
    }
    
    public TestAlertResponse testRule(String ruleKey, TestAlertRequest request) {
        AlertRule rule = ruleRepository.findByKey(ruleKey)
            .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));
        
        // Test the rule with provided data
        // This would involve creating a test location and vehicle ID
        
        TestAlertResponse response = new TestAlertResponse();
        response.setRuleKey(ruleKey);
        response.setWouldTrigger(true); // Simplified
        response.setMessage("Test alert would be triggered");
        
        return response;
    }
    
    private AlertRuleResponse toResponse(AlertRule rule) {
        AlertRuleResponse response = new AlertRuleResponse();
        response.setRuleKey(rule.getRuleKey());
        response.setRuleName(rule.getRuleName());
        response.setRuleType(rule.getRuleType());
        response.setParameters(rule.getParameters());
        response.setPriority(rule.getPriority());
        response.setEnabled(rule.isEnabled());
        response.setCreatedAt(Instant.now()); // Would come from entity
        response.setUpdatedAt(Instant.now());
        return response;
    }
}
