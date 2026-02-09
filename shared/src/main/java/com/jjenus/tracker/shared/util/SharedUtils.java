package com.jjenus.tracker.shared.util;

import java.util.Map;

public class SharedUtils {
    /* =========================================================
       Extractors
       ========================================================= */

    public static Boolean extractAccStatus(Map<String, Object> metadata) {
        Object v = first(metadata,
                LocationMetadataConstants.ACC_STATUS,
                "acc",
                "acc_state");

        if (v == null) {
            Object off = first(metadata,
                    LocationMetadataConstants.ACC_OFF,
                    LocationMetadataConstants.ACC_CLOSE);

            Boolean offBool = convertToBoolean(off);
            return offBool == null ? null : !offBool;
        }

        return convertToBoolean(v);
    }

    public static String extractEngineStatus(Map<String, Object> metadata) {
        Object v = first(metadata,
                LocationMetadataConstants.ENGINE_STATUS,
                "engine",
                "engine_state");

        return v == null ? null : v.toString();
    }

    public static Float extractBatteryLevel(Map<String, Object> metadata) {
        return convertToFloat(first(metadata,
                LocationMetadataConstants.BATTERY_LEVEL,
                LocationMetadataConstants.BATTERY_PERCENT,
                LocationMetadataConstants.BATTERY_VOLTAGE,
                "battery"));
    }

    public static Integer extractGsmSignal(Map<String, Object> metadata) {
        return convertToInteger(first(metadata,
                LocationMetadataConstants.GSM_SIGNAL,
                LocationMetadataConstants.SIGNAL_STRENGTH));
    }

    /* =========================================================
       Helpers
       ========================================================= */

    public static Object first(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v;
        }
        return null;
    }

    public static Boolean convertToBoolean(Object value) {
        if (value == null) return null;

        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() == 1;

        if (value instanceof String s) {
            s = s.toLowerCase();
            return s.equals("true") || s.equals("1") || s.equals("on") || s.equals("yes");
        }

        return null;
    }

    public static Integer convertToInteger(Object value) {
        if (value == null) return null;

        if (value instanceof Number n) return n.intValue();

        if (value instanceof String s) {
            try { return Integer.parseInt(s); }
            catch (Exception ignored) {}
        }

        return null;
    }

    public static Float convertToFloat(Object value) {
        if (value == null) return null;

        if (value instanceof Number n) return n.floatValue();

        if (value instanceof String s) {
            try { return Float.parseFloat(s); }
            catch (Exception ignored) {}
        }

        return null;
    }

    public SharedUtils () {
        throw new UnsupportedOperationException("SharedUtility class - cannot be instantiated");
    }
}
