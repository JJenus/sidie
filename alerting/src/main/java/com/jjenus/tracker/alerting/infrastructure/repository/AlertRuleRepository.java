package com.jjenus.tracker.alerting.infrastructure.repository;

import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.domain.enums.AlertRuleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    Optional<AlertRule> findByRuleKey(String ruleKey);

    List<AlertRule> findByIsEnabled(boolean isEnabled);

    Page<AlertRule> findByIsEnabled(boolean isEnabled, Pageable pageable);

    List<AlertRule> findByRuleType(AlertRuleType ruleType);

    Page<AlertRule> findByRuleType(AlertRuleType ruleType, Pageable pageable);

    List<AlertRule> findByRuleTypeAndIsEnabled(AlertRuleType ruleType, boolean isEnabled);

    Page<AlertRule> findByRuleTypeAndIsEnabled(AlertRuleType ruleType, boolean isEnabled, Pageable pageable);

    @Query("SELECT ar FROM AlertRule ar WHERE ar.isEnabled = true " +
            "ORDER BY ar.priority DESC")
    List<AlertRule> findActiveRulesOrderedByPriority();

    @Query("SELECT ar FROM AlertRule ar WHERE ar.isEnabled = true " +
            "ORDER BY ar.priority DESC")
    Page<AlertRule> findActiveRulesOrderedByPriority(Pageable pageable);

    @Query("SELECT ar FROM AlertRule ar WHERE ar.isEnabled = true " +
            "AND (:vehicleId MEMBER OF ar.vehicleIds OR :vehicleId IS NULL) " +
            "ORDER BY ar.priority DESC")
    List<AlertRule> findActiveRulesForVehicle(@Param("vehicleId") String vehicleId);

    @Query("SELECT ar FROM AlertRule ar WHERE ar.isEnabled = true " +
            "AND (:vehicleId MEMBER OF ar.vehicleIds OR :vehicleId IS NULL)")
    Page<AlertRule> findActiveRulesForVehicle(@Param("vehicleId") String vehicleId, Pageable pageable);

    // Find all vehicles that have rules
    @Query("SELECT DISTINCT vid FROM AlertRule ar JOIN ar.vehicleIds vid WHERE ar.isEnabled = true")
    Set<String> findVehiclesWithActiveRules();

    boolean existsByRuleKey(String ruleKey);

    void deleteByRuleKey(String ruleKey);

    // Search methods with pagination
    @Query("SELECT ar FROM AlertRule ar WHERE " +
            "(:search IS NULL OR LOWER(ar.ruleName) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:ruleType IS NULL OR ar.ruleType = :ruleType) " +
            "AND (:enabled IS NULL OR ar.isEnabled = :enabled)")
    Page<AlertRule> searchAlertRules(
            @Param("search") String search,
            @Param("ruleType") AlertRuleType ruleType,
            @Param("enabled") Boolean enabled,
            Pageable pageable);
}