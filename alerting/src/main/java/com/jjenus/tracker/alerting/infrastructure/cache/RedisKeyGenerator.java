package com.jjenus.tracker.alerting.infrastructure.cache;

import org.springframework.stereotype.Component;

@Component
public class RedisKeyGenerator {

    // ========== CACHE NAMESPACING & VERSIONING ==========
    public static final String CACHE_NAMESPACE = "tracker:alerting:";
    public static final String CACHE_VERSION = "v1:";

    // ========== ALERT RULE KEYS ==========
    private static final String RULES_PREFIX = "rules:";
    public static final String VEHICLE_RULES_KEY_PREFIX = RULES_PREFIX + "vehicle:";
    public static final String VEHICLES_WITH_RULES_KEY = RULES_PREFIX + "vehicles:with-rules";
    public static final String RULE_DETAIL_KEY_PREFIX = RULES_PREFIX + "detail:";
    public static final String ALL_ACTIVE_RULES_KEY = RULES_PREFIX + "active:all";
    public static final String RULE_VEHICLE_INDEX_KEY = RULES_PREFIX + "vehicle-index";

    // ========== GEOFENCE KEYS ==========
    private static final String GEOFENCE_PREFIX = "geofence:";
    public static final String GEOFENCE_DETAIL_KEY_PREFIX = GEOFENCE_PREFIX + "detail:";
    public static final String VEHICLE_GEOFENCES_KEY_PREFIX = GEOFENCE_PREFIX + "vehicle:all:";
    public static final String ACTIVE_VEHICLE_GEOFENCES_KEY_PREFIX = GEOFENCE_PREFIX + "vehicle:active:";
    public static final String GEOFENCE_VEHICLE_INDEX_KEY = GEOFENCE_PREFIX + "vehicle-index";

    // ========== ALERT DATA KEYS ==========
    private static final String ALERT_PREFIX = "alert:";
    public static final String VEHICLE_ALERT_DATA_KEY_PREFIX = ALERT_PREFIX + "vehicle:data:";
    public static final String ALERT_COOLDOWN_KEY_PREFIX = ALERT_PREFIX + "cooldown:";

    // ========== STATISTICS & METRICS KEYS ==========
    private static final String STATS_PREFIX = "stats:";
    public static final String RULE_TRIGGER_COUNT_KEY = STATS_PREFIX + "rule:trigger-count:";
    public static final String VEHICLE_ALERT_COUNT_KEY = STATS_PREFIX + "vehicle:alert-count:";

    // ========== PAGINATION KEYS ==========
    private static final String PAGINATION_PREFIX = "pagination:";
    public static final String PAGINATED_RULES_KEY_PREFIX = PAGINATION_PREFIX + "rules:";
    public static final String PAGINATED_GEOFENCES_KEY_PREFIX = PAGINATION_PREFIX + "geofences:";

    // ========== CACHE TTLs (in seconds) ==========
    public static final long VEHICLE_RULE_CACHE_TTL = 2 * 60 * 60; // 2 hours
    public static final long INDEX_CACHE_TTL = 30 * 60; // 30 minutes
    public static final long RULE_DETAIL_TTL = 24 * 60 * 60; // 24 hours
    public static final long GEOFENCE_CACHE_TTL = 4 * 60 * 60; // 4 hours
    public static final long VEHICLE_GEOFENCE_CACHE_TTL = 60 * 60; // 1 hour
    public static final long ALERT_COOLDOWN_TTL = 10 * 60; // 10 minutes
    public static final long STATS_TTL = 7 * 24 * 60 * 60; // 7 days
    public static final long PAGINATION_CACHE_TTL = 5 * 60; // 5 minutes

    // ========== ALERT RULE METHODS ==========
    public String getVehicleRulesKey(String vehicleId) {
        return buildKey(VEHICLE_RULES_KEY_PREFIX + vehicleId);
    }

    public String getVehiclesWithRulesKey() {
        return buildKey(VEHICLES_WITH_RULES_KEY);
    }

    public String getRuleDetailKey(String ruleKey) {
        return buildKey(RULE_DETAIL_KEY_PREFIX + ruleKey);
    }

    public String getAllActiveRulesKey() {
        return buildKey(ALL_ACTIVE_RULES_KEY);
    }

    public String getRuleVehicleIndexKey() {
        return buildKey(RULE_VEHICLE_INDEX_KEY);
    }

    // ========== GEOFENCE METHODS ==========
    public String getGeofenceDetailKey(Long geofenceId) {
        return buildKey(GEOFENCE_DETAIL_KEY_PREFIX + geofenceId);
    }

    public String getVehicleGeofencesKey(String vehicleId) {
        return buildKey(VEHICLE_GEOFENCES_KEY_PREFIX + vehicleId);
    }

    public String getActiveVehicleGeofencesKey(String vehicleId) {
        return buildKey(ACTIVE_VEHICLE_GEOFENCES_KEY_PREFIX + vehicleId);
    }

    public String getGeofenceVehicleIndexKey() {
        return buildKey(GEOFENCE_VEHICLE_INDEX_KEY);
    }

    // ========== ALERT DATA METHODS ==========
    public String getVehicleAlertDataKey(String vehicleId) {
        return buildKey(VEHICLE_ALERT_DATA_KEY_PREFIX + vehicleId);
    }

    public String getAlertCooldownKey(String ruleKey, String vehicleId) {
        return buildKey(ALERT_COOLDOWN_KEY_PREFIX + ruleKey + ":" + vehicleId);
    }

    // ========== STATISTICS METHODS ==========
    public String getRuleTriggerCountKey(String ruleKey) {
        return buildKey(RULE_TRIGGER_COUNT_KEY + ruleKey);
    }

    public String getVehicleAlertCountKey(String vehicleId) {
        return buildKey(VEHICLE_ALERT_COUNT_KEY + vehicleId);
    }

    // ========== PAGINATION METHODS ==========
    public String getPaginatedRulesKey(int page, int size, String sortBy, String sortDirection,
                                       String search, String ruleType, Boolean enabled) {
        String key = String.format("page:%d:size:%d:sort:%s:dir:%s",
                page, size, sortBy, sortDirection);
        if (search != null && !search.trim().isEmpty()) key += ":search:" + search.hashCode();
        if (ruleType != null) key += ":type:" + ruleType;
        if (enabled != null) key += ":enabled:" + enabled;
        return buildKey(PAGINATED_RULES_KEY_PREFIX + key);
    }

    public String getPaginatedGeofencesKey(int page, int size, String sortBy, String sortDirection,
                                           String search, String vehicleId, Boolean active) {
        String key = String.format("page:%d:size:%d:sort:%s:dir:%s",
                page, size, sortBy, sortDirection);
        if (search != null && !search.trim().isEmpty()) key += ":search:" + search.hashCode();
        if (vehicleId != null) key += ":vehicle:" + vehicleId;
        if (active != null) key += ":active:" + active;
        return buildKey(PAGINATED_GEOFENCES_KEY_PREFIX + key);
    }

    // ========== UTILITY METHODS ==========
    public String getLockKey(String resource, String identifier) {
        return buildKey("lock:" + resource + ":" + identifier);
    }

    public String getPatternKey(String pattern) {
        return buildKey(pattern + "*");
    }

    // ========== PRIVATE HELPER ==========
    private String buildKey(String key) {
        return CACHE_NAMESPACE + CACHE_VERSION + key;
    }

    // ========== PATTERN GENERATORS ==========
    public String getVehicleRulesPattern() {
        return buildKey(VEHICLE_RULES_KEY_PREFIX + "*");
    }

    public String getGeofenceDetailPattern() {
        return buildKey(GEOFENCE_DETAIL_KEY_PREFIX + "*");
    }

    public String getVehicleGeofencesPattern() {
        return buildKey(VEHICLE_GEOFENCES_KEY_PREFIX + "*");
    }

    public String getAllRuleKeysPattern() {
        return buildKey(RULE_DETAIL_KEY_PREFIX + "*");
    }

    public String getPaginatedRulesPattern() {
        return buildKey(PAGINATED_RULES_KEY_PREFIX + "*");
    }

    public String getPaginatedGeofencesPattern() {
        return buildKey(PAGINATED_GEOFENCES_KEY_PREFIX + "*");
    }

    // ========== SHORT TTL KEYS (For rate limiting, etc.) ==========
    public String getRateLimitKey(String vehicleId, String ruleType) {
        return buildKey("rate-limit:" + ruleType + ":" + vehicleId + ":" + System.currentTimeMillis() / 60000);
    }

    // ========== BATCH OPERATION KEYS ==========
    public String getBatchOperationKey(String operationId) {
        return buildKey("batch:" + operationId);
    }
}