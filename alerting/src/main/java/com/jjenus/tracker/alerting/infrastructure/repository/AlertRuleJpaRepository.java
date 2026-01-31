package com.jjenus.tracker.alerting.infrastructure.repository;

import com.jjenus.tracker.alerting.domain.AlertRule;
import com.jjenus.tracker.alerting.domain.AlertRuleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class AlertRuleJpaRepository implements AlertRuleRepository {
    
    private final ConcurrentMap<String, AlertRule> rules = new ConcurrentHashMap<>();
    
    @Override
    public AlertRule save(AlertRule rule) {
        rules.put(rule.getRuleKey(), rule);
        return rule;
    }
    
    @Override
    public Optional<AlertRule> findByKey(String ruleKey) {
        return Optional.ofNullable(rules.get(ruleKey));
    }
    
    @Override
    public List<AlertRule> findAll() {
        return List.copyOf(rules.values());
    }
    
    @Override
    public List<AlertRule> findAllEnabled() {
        return rules.values().stream()
            .filter(AlertRule::isEnabled)
            .toList();
    }
    
    @Override
    public void delete(String ruleKey) {
        rules.remove(ruleKey);
    }
    
    @Override
    public boolean existsByKey(String ruleKey) {
        return rules.containsKey(ruleKey);
    }
}
