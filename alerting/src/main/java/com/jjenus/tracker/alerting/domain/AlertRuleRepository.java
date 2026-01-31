package com.jjenus.tracker.alerting.domain;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository {
    AlertRule save(AlertRule rule);
    Optional<AlertRule> findByKey(String ruleKey);
    List<AlertRule> findAll();
    List<AlertRule> findAllEnabled();
    void delete(String ruleKey);
    boolean existsByKey(String ruleKey);
}
