package com.jjenus.tracker.alerting.infrastructure.cache;

import com.jjenus.tracker.alerting.domain.entity.AlertRule;
import com.jjenus.tracker.alerting.infrastructure.repository.AlertRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class VehicleRuleCacheService {

    private static final Logger logger = LoggerFactory.getLogger(VehicleRuleCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final AlertRuleRepository ruleRepository;
    private final RedisKeyGenerator keyGenerator;

    public VehicleRuleCacheService(
            RedisTemplate<String, Object> redisTemplate,
            AlertRuleRepository ruleRepository,
            RedisKeyGenerator keyGenerator) {
        this.redisTemplate = redisTemplate;
        this.ruleRepository = ruleRepository;
        this.keyGenerator = keyGenerator;
    }

    /**
     * Get active rules for a specific vehicle
     */
    public List<AlertRule> getActiveRulesForVehicle(String vehicleId) {
        try {
            String cacheKey = keyGenerator.getVehicleRulesKey(vehicleId);

            // Check cache first
            List<Object> cachedRules = redisTemplate.opsForList().range(cacheKey, 0, -1);

            if (cachedRules != null && !cachedRules.isEmpty()) {
                return cachedRules.stream()
                        .map(obj -> (AlertRule) obj)
                        .collect(Collectors.toList());
            }

            // Cache miss - load from DB and cache
            return loadAndCacheRulesForVehicle(vehicleId);

        } catch (Exception e) {
            logger.error("Failed to get rules for vehicle {}", vehicleId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Load and cache rules for vehicle
     */
    private List<AlertRule> loadAndCacheRulesForVehicle(String vehicleId) {
        try {
            // Query rules that apply to this vehicle
            List<AlertRule> rules = ruleRepository.findActiveRulesForVehicle(vehicleId);

            if (rules.isEmpty()) {
                // Cache empty result with short TTL
                cacheEmptyResult(vehicleId);
                return Collections.emptyList();
            }

            // Sort by priority descending and cache
            List<AlertRule> sortedRules = rules.stream()
                    .sorted(Comparator.comparingInt(AlertRule::getPriority).reversed())
                    .collect(Collectors.toList());

            cacheRules(vehicleId, sortedRules);

            logger.debug("Loaded {} rules for vehicle {}", sortedRules.size(), vehicleId);

            return sortedRules;

        } catch (Exception e) {
            logger.error("Failed to load rules for vehicle {}", vehicleId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Cache rules for vehicle
     */
    private void cacheRules(String vehicleId, List<AlertRule> rules) {
        try {
            String cacheKey = keyGenerator.getVehicleRulesKey(vehicleId);

            // Delete existing
            redisTemplate.delete(cacheKey);

            if (!rules.isEmpty()) {
                // Push all rules to list
                redisTemplate.opsForList().rightPushAll(cacheKey, rules.toArray());

                // Set TTL
                redisTemplate.expire(cacheKey, RedisKeyGenerator.VEHICLE_RULE_CACHE_TTL, TimeUnit.SECONDS);
            }

            logger.debug("Cached {} rules for vehicle {}", rules.size(), vehicleId);

        } catch (Exception e) {
            logger.error("Failed to cache rules for vehicle {}", vehicleId, e);
        }
    }

    /**
     * Cache empty result (no rules for this vehicle)
     */
    private void cacheEmptyResult(String vehicleId) {
        try {
            String cacheKey = keyGenerator.getVehicleRulesKey(vehicleId);

            // Store special marker for empty result with short TTL
            redisTemplate.opsForValue().set(cacheKey, "EMPTY", 5, TimeUnit.MINUTES);

            logger.debug("Cached empty result for vehicle {}", vehicleId);

        } catch (Exception e) {
            logger.error("Failed to cache empty result for vehicle {}", vehicleId, e);
        }
    }

    /**
     * Check if vehicle has any active rules (quick cache check)
     */
    public boolean hasActiveRules(String vehicleId) {
        try {
            String cacheKey = keyGenerator.getVehicleRulesKey(vehicleId);
            Object cached = redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                if ("EMPTY".equals(cached)) {
                    return false;
                }
                return redisTemplate.opsForList().size(cacheKey) > 0;
            }

            // Cache miss - check if we should even try
            return !getActiveRulesForVehicle(vehicleId).isEmpty();

        } catch (Exception e) {
            logger.error("Failed to check rules for vehicle {}", vehicleId, e);
            return false; // Be conservative - assume no rules
        }
    }

    /**
     * Get all vehicles that have active rules (for bulk operations)
     */
    public Set<String> getVehiclesWithActiveRules() {
        try {
            // This could be cached separately for faster lookups
            Set<String> vehiclesWithRules = ruleRepository.findVehiclesWithActiveRules();

            // Cache this set as well for quick reference
            String key = keyGenerator.getVehiclesWithRulesKey();
            redisTemplate.delete(key);

            if (!vehiclesWithRules.isEmpty()) {
                redisTemplate.opsForSet().add(key, vehiclesWithRules.toArray());
                redisTemplate.expire(key, keyGenerator.INDEX_CACHE_TTL, TimeUnit.SECONDS);
            }

            return vehiclesWithRules;

        } catch (Exception e) {
            logger.error("Failed to get vehicles with active rules", e);
            return Collections.emptySet();
        }
    }

    /**
     * Quick check if vehicle has rules (using cached index)
     */
    public boolean hasRulesCached(String vehicleId) {
        try {
            String key = keyGenerator.getVehiclesWithRulesKey();
            Boolean hasRule = redisTemplate.opsForSet().isMember(key, vehicleId);
            return Boolean.TRUE.equals(hasRule);
        } catch (Exception e) {
            logger.error("Failed to check cached rules index for vehicle {}", vehicleId, e);
            return false;
        }
    }

    /**
     * Invalidate cache for specific vehicle
     */
    public void invalidateVehicleRules(String vehicleId) {
        try {
            String cacheKey = keyGenerator.getVehicleRulesKey(vehicleId);
            redisTemplate.delete(cacheKey);

            // Also remove from vehicles-with-rules index
            String indexKey = keyGenerator.getVehiclesWithRulesKey();
            redisTemplate.opsForSet().remove(indexKey, vehicleId);

            logger.debug("Invalidated cache for vehicle {}", vehicleId);
        } catch (Exception e) {
            logger.error("Failed to invalidate cache for vehicle {}", vehicleId, e);
        }
    }

    /**
     * Batch invalidate - when rules change
     */
    public void invalidateAllVehicleRules() {
        try {
            Set<String> keys = redisTemplate.keys(keyGenerator.getVehicleRulesKey("*"));
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }

            // Clear vehicles index
            String indexKey = keyGenerator.getVehiclesWithRulesKey();
            redisTemplate.delete(indexKey);

            logger.info("Invalidated all vehicle rule caches");
        } catch (Exception e) {
            logger.error("Failed to invalidate all vehicle caches", e);
        }
    }

    /**
     * Pre-warm cache for frequently accessed vehicles
     */
    public void prewarmCacheForHighPriorityVehicles() {
        try {
            Set<String> highPriorityVehicles = getHighPriorityVehicles();

            for (String vehicleId : highPriorityVehicles) {
                // Async load into cache
                loadAndCacheRulesForVehicle(vehicleId);
            }

            logger.info("Pre-warmed cache for {} vehicles", highPriorityVehicles.size());

        } catch (Exception e) {
            logger.error("Failed to pre-warm cache", e);
        }
    }

    private Set<String> getHighPriorityVehicles() {
        // Business logic: VIP vehicles, recently active, etc.
        // Could be from configuration, analytics, or recent activity
        return new HashSet<>(); // Implement based on your needs
    }
}