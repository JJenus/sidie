package com.jjenus.tracker.core.infrastructure.repository;

import com.jjenus.tracker.core.domain.entity.AlertRule;
import com.jjenus.tracker.core.domain.enums.AlertRuleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
    
    Optional<AlertRule> findByRuleKey(String ruleKey);
    
    List<AlertRule> findByIsEnabled(boolean isEnabled);
    
    List<AlertRule> findByRuleType(AlertRuleType ruleType);
    
    List<AlertRule> findByRuleTypeAndIsEnabled(AlertRuleType ruleType, boolean isEnabled);
    
    @Query("SELECT ar FROM AlertRule ar WHERE ar.isEnabled = true " +
           "ORDER BY ar.priority DESC")
    List<AlertRule> findActiveRulesOrderedByPriority();
    
    boolean existsByRuleKey(String ruleKey);
}