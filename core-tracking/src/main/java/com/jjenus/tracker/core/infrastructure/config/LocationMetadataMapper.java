package com.jjenus.tracker.core.infrastructure.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjenus.tracker.core.domain.entity.TrackerLocation;
import com.jjenus.tracker.shared.util.LocationMetadataConstants;
import com.jjenus.tracker.shared.util.SharedUtils;
import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public class LocationMetadataMapper {

    private static final ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /*
     * Deterministic pipeline:
     * 1. Jackson: structural field binding (matching names only)
     * 2. Explicit conversions / aliases
     * 3. Persist raw metadata JSON
     */
    public void mapMetadataToLocation(
            TrackerLocation location,
            Map<String, Object> metadata
    ) {

        if (metadata == null || metadata.isEmpty()) {
            return;
        }

        // ---- Step 1: structural binding (Map -> POJO)
        TrackerLocation partial =
                jsonMapper.convertValue(metadata, TrackerLocation.class);

        copyNonNull(partial, location);

        // ---- Step 2: explicit protocol normalization
        mapSpecificFields(location, metadata);

        // ---- Step 3: store raw JSON snapshot
        storeMetadataAsJson(location, metadata);
    }

    /* =========================================================
       Structural merge
       ========================================================= */

    private void copyNonNull(TrackerLocation src, TrackerLocation dst) {
        if (src.getHeading() != null) dst.setHeading(src.getHeading());
        if (src.getAltitude() != null) dst.setAltitude(src.getAltitude());
        if (src.getOdometerKm() != null) dst.setOdometerKm(src.getOdometerKm());
        if (src.getBatteryVoltage() != null) dst.setBatteryVoltage(src.getBatteryVoltage());
        if (src.getSignalStrength() != null) dst.setSignalStrength(src.getSignalStrength());
        if (src.getAccStatus() != null) dst.setAccStatus(src.getAccStatus());
        if (src.getEngineStatus() != null) dst.setEngineStatus(src.getEngineStatus());
    }

    /* =========================================================
       Explicit conversions / aliases
       ========================================================= */

    private void mapSpecificFields(TrackerLocation location, Map<String, Object> metadata) {

        location.setAccStatus(SharedUtils.extractAccStatus(metadata));
        location.setSignalStrength(SharedUtils.extractGsmSignal(metadata));
        location.setBatteryVoltage(SharedUtils.extractBatteryLevel(metadata));
        location.setHeading(SharedUtils.convertToFloat(metadata.get(LocationMetadataConstants.HEADING)));
        location.setAltitude(SharedUtils.convertToFloat(metadata.get(LocationMetadataConstants.ALTITUDE)));
        location.setOdometerKm(SharedUtils.convertToFloat(metadata.get(LocationMetadataConstants.ODOMETER_KM)));
        location.setEngineStatus(SharedUtils.extractEngineStatus(metadata));
    }

    private void storeMetadataAsJson(TrackerLocation location, Map<String, Object> metadata) {
        try {
            location.setDeviceStatus(jsonMapper.writeValueAsString(metadata));
        } catch (Exception e) {
            location.setDeviceStatus(metadata.toString());
        }
    }
}