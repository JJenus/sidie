package com.jjenus.tracker.alerting.application;

import com.jjenus.tracker.alerting.api.dto.AlertRuleResponse;
import com.jjenus.tracker.alerting.domain.AlertRule;
import com.jjenus.tracker.alerting.domain.AlertRuleRepository;
import com.jjenus.tracker.alerting.exception.AlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AlertRuleQueryService {
    
    private final AlertRuleRepository ruleRepository;
    
    public AlertRuleQueryService(AlertRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }
    
    public List<AlertRuleResponse> getAllRules() {
        return ruleRepository.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }
    
    public List<AlertRuleResponse> getEnabledRules() {
        return ruleRepository.findAllEnabled().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }
    
    public AlertRuleResponse getRuleByKey(String ruleKey) {
        AlertRule rule = ruleRepository.findByKey(ruleKey)
            .orElseThrow(() -> AlertException.ruleNotFound(ruleKey));
        return toResponse(rule);
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
