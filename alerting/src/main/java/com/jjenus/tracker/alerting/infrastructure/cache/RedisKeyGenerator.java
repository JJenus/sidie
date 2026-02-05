package com.jjenus.tracker.alerting.infrastructure.cache;

import org.springframework.stereotype.Component;

@Component
public class RedisKeyGenerator {

    // ... existing code ...

    // ========== ALERT CACHE KEYS ==========
    private static final String ALERT_PREFIX = "alert:";
    public static final String ALERT_DETAIL_KEY_PREFIX = ALERT_PREFIX + "detail:";
    public static final String ACTIVE_VEHICLE_ALERTS_KEY_PREFIX = ALERT_PREFIX + "vehicle:active:";
    public static final String RECENT_VEHICLE_ALERTS_KEY_PREFIX = ALERT_PREFIX + "vehicle:recent:";
    public static final String ALERT_STATISTICS_KEY_PREFIX = ALERT_PREFIX + "stats:";

    // ========== ALERT CACHE METHODS ==========
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

    // ... rest of existing code ...
}
