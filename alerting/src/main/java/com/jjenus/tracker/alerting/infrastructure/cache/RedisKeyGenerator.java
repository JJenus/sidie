package com.jjenus.tracker.alerting.infrastructure.cache;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Component
public class RedisKeyGenerator {

    private static final String PREFIX = "alerting:";

    // ========== ALERT CACHE KEYS ==========
    private static final String ALERT_PREFIX = PREFIX + "alert:";
    public static final String ALERT_DETAIL_KEY_PREFIX = ALERT_PREFIX + "detail:";
    public static final String ACTIVE_VEHICLE_ALERTS_KEY_PREFIX = ALERT_PREFIX + "vehicle:active:";
    public static final String RECENT_VEHICLE_ALERTS_KEY_PREFIX = ALERT_PREFIX + "vehicle:recent:";
    public static final String ALERT_STATISTICS_KEY_PREFIX = ALERT_PREFIX + "stats:";

    // ========== ALERT RULE CACHE KEYS ==========
    private static final String RULE_PREFIX = PREFIX + "rule:";
    public static final String RULE_DETAIL_KEY_PREFIX = RULE_PREFIX + "detail:";
    public static final String ALL_ACTIVE_RULES_KEY = RULE_PREFIX + "active:all";
    public static final String VEHICLES_WITH_RULES_KEY = RULE_PREFIX + "vehicles:with:rules";
    public static final long RULE_DETAIL_TTL = 3600L;
    public static final long VEHICLE_RULE_CACHE_TTL = 300L;
    public static final long INDEX_CACHE_TTL = 600L;

    // ========== GEOFENCE CACHE KEYS ==========
    private static final String GEOFENCE_PREFIX = PREFIX + "geofence:";
    public static final String GEOFENCE_DETAIL_KEY_PREFIX = GEOFENCE_PREFIX + "detail:";
    public static final String VEHICLE_GEOFENCES_KEY_PREFIX = GEOFENCE_PREFIX + "vehicle:";
    public static final String ACTIVE_VEHICLE_GEOFENCES_KEY_PREFIX = GEOFENCE_PREFIX + "vehicle:active:";
    public static final String PAGINATED_GEOFENCES_KEY_PREFIX = GEOFENCE_PREFIX + "paginated:";
    public static final long GEOFENCE_CACHE_TTL = 3600L;
    public static final long VEHICLE_GEOFENCE_CACHE_TTL = 600L;
    public static final long PAGINATION_CACHE_TTL = 300L;

    // ========== ALERT RULE METHODS ==========
    public String getRuleDetailKey(String ruleKey) {
        return buildKey(RULE_DETAIL_KEY_PREFIX + ruleKey);
    }

    public String getAllActiveRulesKey() {
        return ALL_ACTIVE_RULES_KEY;
    }

    public String getVehicleRulesKey(String vehicleId) {
        return buildKey(RULE_PREFIX + "vehicle:" + vehicleId);
    }

    public String getVehiclesWithRulesKey() {
        return VEHICLES_WITH_RULES_KEY;
    }

    // ========== ALERT METHODS ==========
    public String getAlertDetailKey(Long alertId) {
        return buildKey(ALERT_DETAIL_KEY_PREFIX + alertId);
    }

    public String getActiveVehicleAlertsKey(String vehicleId) {
        return buildKey(ACTIVE_VEHICLE_ALERTS_KEY_PREFIX + vehicleId);
    }

    public String getRecentVehicleAlertsKey(String vehicleId) {
        return buildKey(RECENT_VEHICLE_ALERTS_KEY_PREFIX + vehicleId);
    }

    public String getAlertStatisticsKey(String key) {
        return buildKey(ALERT_STATISTICS_KEY_PREFIX + key);
    }

    public String getAlertStatisticsPattern() {
        return buildKey(ALERT_STATISTICS_KEY_PREFIX + "*");
    }

    public String getAllAlertKeysPattern() {
        return buildKey(ALERT_PREFIX + "*");
    }

    // ========== GEOFENCE METHODS ==========
    public String getGeofenceDetailKey(Long geofenceId) {
        return buildKey(GEOFENCE_DETAIL_KEY_PREFIX + geofenceId);
    }

    public String getGeofenceDetailKey(String geofenceId) {
        return buildKey(GEOFENCE_DETAIL_KEY_PREFIX + geofenceId);
    }

    public String getVehicleGeofencesKey(String vehicleId) {
        return buildKey(VEHICLE_GEOFENCES_KEY_PREFIX + vehicleId);
    }

    public String getActiveVehicleGeofencesKey(String vehicleId) {
        return buildKey(ACTIVE_VEHICLE_GEOFENCES_KEY_PREFIX + vehicleId);
    }

    public String getPaginatedGeofencesKey(int page, int size, String sortBy, String sortDir,
                                            String search, String vehicleId, Boolean active) {
        String key = buildKey(PAGINATED_GEOFENCES_KEY_PREFIX +
                hashKey(page + "|" + size + "|" + sortBy + "|" + sortDir + "|" +
                        (search != null ? search : "") + "|" +
                        (vehicleId != null ? vehicleId : "") + "|" +
                        (active != null ? active.toString() : "")));
        return key;
    }

    public String getPaginatedGeofencesPattern() {
        return buildKey(PAGINATED_GEOFENCES_KEY_PREFIX + "*");
    }

    public String getPaginatedRulesKey(Integer page, Integer size, String sortBy, String sortDir,
                                        String search, String ruleType, Boolean enabled) {
        String key = buildKey(RULE_PREFIX + "paginated:" +
                hashKey((page != null ? page : 0) + "|" + (size != null ? size : 0) + "|" +
                        (sortBy != null ? sortBy : "") + "|" + (sortDir != null ? sortDir : "") + "|" +
                        (search != null ? search : "") + "|" +
                        (ruleType != null ? ruleType : "") + "|" +
                        (enabled != null ? enabled.toString() : "")));
        return key;
    }

    public String getPaginatedRulesPattern() {
        return buildKey(RULE_PREFIX + "paginated:*");
    }

    // ========== UTILITY ==========
    private String buildKey(String key) {
        return key;
    }

    private String hashKey(String input) {
        return Integer.toHexString(input.hashCode());
    }
}
