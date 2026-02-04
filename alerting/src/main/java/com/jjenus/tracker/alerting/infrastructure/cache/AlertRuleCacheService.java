package com.jjenus.tracker.alerting.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.alerting.domain.IAlertRule;
import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class AlertRuleCacheService {

    private static final Logger logger = LoggerFactory.getLogger(AlertRuleCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final AlertRuleRepository ruleRepository;
    private final RedisKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    public AlertRuleCacheService(
            RedisTemplate<String, Object> redisTemplate,
            AlertRuleRepository ruleRepository,
            RedisKeyGenerator keyGenerator,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.ruleRepository = ruleRepository;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * Load all active rules into cache on startup or when rules change
     */
    public void loadAllActiveRules() {
        try {
            List<AlertRule> activeRules = ruleRepository.findActiveRulesOrderedByPriority();

            // Clear existing cache
            redisTemplate.delete(keyGenerator.getAllActiveRulesKey());

            // Store each rule
            for (AlertRule rule : activeRules) {
                cacheRule(rule);
            }

            logger.info("Loaded {} active alert rules into Redis cache", activeRules.size());

        } catch (Exception e) {
            logger.error("Failed to load active rules into cache", e);
        }
    }

    /**
     * Get all active rules from cache
     */
    public List<AlertRule> getAllActiveRules() {
        try {
            // Get all rule keys from the active rules set
            Set<Object> ruleKeys = redisTemplate.opsForSet()
                    .members(keyGenerator.getAllActiveRulesKey());

            if (ruleKeys == null || ruleKeys.isEmpty()) {
                return loadAllActiveRulesAndReturn();
            }

            // Fetch all rules in bulk using pipelining
            return redisTemplate.executePipelined((RedisCallback<?>) connection -> {
                        for (Object ruleKeyObj : ruleKeys) {
                            String ruleKey = (String) ruleKeyObj;
                            connection.stringCommands().get(
                                    keyGenerator.getRuleDetailKey(ruleKey).getBytes()
                            );
                        }
                        return null;
                    }).stream()
                    .filter(Objects::nonNull)
                    .map(data -> objectMapper.convertValue(data, AlertRule.class))
                    .sorted(Comparator.comparingInt(AlertRule::getPriority).reversed())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Failed to get active rules from cache, falling back to DB", e);
            return ruleRepository.findActiveRulesOrderedByPriority();
        }
    }

    /**
     * Cache a single rule
     */
    public void cacheRule(AlertRule rule) {
        try {
            String ruleKey = rule.getRuleKey();

            // Store rule details with TTL
            redisTemplate.opsForValue().set(
                    keyGenerator.getRuleDetailKey(ruleKey),
                    rule,
                    RedisKeyGenerator.RULE_DETAIL_TTL,
                    TimeUnit.SECONDS
            );

            // Add to active rules set if enabled
            if (Boolean.TRUE.equals(rule.getIsEnabled())) {
                redisTemplate.opsForSet().add(
                        keyGenerator.getAllActiveRulesKey(),
                        ruleKey
                );
                // Set TTL on the set
                redisTemplate.expire(
                        keyGenerator.getAllActiveRulesKey(),
                        RedisKeyGenerator.RULE_DETAIL_TTL,
                        TimeUnit.SECONDS
                );
            }

            logger.debug("Cached rule: {}", ruleKey);

        } catch (Exception e) {
            logger.error("Failed to cache rule: {}", rule.getRuleKey(), e);
        }
    }

    /**
     * Remove rule from cache
     */
    public void evictRule(String ruleKey) {
        try {
            // Remove from active rules set
            redisTemplate.opsForSet().remove(
                    keyGenerator.getAllActiveRulesKey(),
                    ruleKey
            );

            // Remove rule details
            redisTemplate.delete(keyGenerator.getRuleDetailKey(ruleKey));

            logger.debug("Evicted rule from cache: {}", ruleKey);

        } catch (Exception e) {
            logger.error("Failed to evict rule from cache: {}", ruleKey, e);
        }
    }

    /**
     * Get rule by key from cache
     */
    public Optional<AlertRule> getRuleByKey(String ruleKey) {
        try {
            AlertRule rule = (AlertRule) redisTemplate.opsForValue()
                    .get(keyGenerator.getRuleDetailKey(ruleKey));

            if (rule != null) {
                // Refresh TTL
                redisTemplate.expire(
                        keyGenerator.getRuleDetailKey(ruleKey),
                        RedisKeyGenerator.RULE_DETAIL_TTL,
                        TimeUnit.SECONDS
                );
                return Optional.of(rule);
            }

        } catch (Exception e) {
            logger.error("Failed to get rule from cache: {}", ruleKey, e);
        }
        return Optional.empty();
    }

    /**
     * Clear all rule cache
     */
    public void clearAllCache() {
        try {
            // Get all cache keys matching the pattern
            Set<String> keys = redisTemplate.keys("alerting:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("Cleared all alert rule cache entries");
            }
        } catch (Exception e) {
            logger.error("Failed to clear cache", e);
        }
    }

    private List<AlertRule> loadAllActiveRulesAndReturn() {
        List<AlertRule> rules = ruleRepository.findActiveRulesOrderedByPriority();
        loadAllActiveRules(); // Re-populate cache
        return rules;
    }
}