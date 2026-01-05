package com.jjenus.tracker.devicecomm.infrastructure;

import com.jjenus.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.jjenus.tracker.core.domain.LocationPoint;
import com.jjenus.tracker.devicecomm.exception.ProtocolException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class AutoseekerProtocolParser implements ITrackerProtocolParser {

    @Override
    public LocationPoint parse(String rawData) throws ProtocolException { // Changed from byte[] to String
        try {
            if (!canParse(rawData)) {
                throw ProtocolException.invalidHeader("Autoseeker");
            }
            
            // Parse the example format: *XX,YYYYYYYYYY,V1,HHMMSS,S,latitude,D,longitude,G,speed,direction,DDMMYY,vehicle_status,...
            String[] parts = rawData.split(",");
            
            if (parts.length < 11) {
                throw ProtocolException.parseError("Autoseeker", "Incomplete data packet");
            }
            
            String deviceId = parts[1]; // YYYYYYYYYY is device ID
            String timeStr = parts[3]; // HHMMSS
            
            // Parse latitude
            String latDir = parts[5]; // S or N
            double lat = Double.parseDouble(parts[4]);
            if ("S".equals(latDir)) {
                lat = -lat;
            }
            
            // Parse longitude
            String lonDir = parts[7]; // E or W
            double lng = Double.parseDouble(parts[6]);
            if ("W".equals(lonDir)) {
                lng = -lng;
            }
            
            // Parse speed
            float speed = Float.parseFloat(parts[8]);
            
            // Parse date
            String dateStr = parts[10]; // DDMMYY
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("ddMMyyHHmmss");
            String dateTimeStr = dateStr + timeStr;
            Instant timestamp = java.time.LocalDateTime.parse(dateTimeStr, dateFormatter)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant();
            
            return new LocationPoint(lat, lng, speed, timestamp);
            
        } catch (ProtocolException e) {
            throw e;
        } catch (Exception e) {
            throw ProtocolException.parseError("Autoseeker", e.getMessage());
        }
    }

    @Override
    public boolean canParse(String rawData) { // Changed from byte[] to String
        if (rawData == null || rawData.isEmpty()) return false;
        return rawData.startsWith("*XX,");
    }

    @Override
    public String buildFuelCutCommand(String deviceId) {
        try {
            return String.format("*CMD,%s,FUEL,OFF#", deviceId);
        } catch (Exception e) {
            throw ProtocolException.commandBuildError("Autoseeker", "fuel cut");
        }
    }

    @Override
    public String buildEngineOnCommand(String deviceId) {
        try {
            return String.format("*CMD,%s,FUEL,ON#", deviceId);
        } catch (Exception e) {
            throw ProtocolException.commandBuildError("Autoseeker", "engine on");
        }
    }

    @Override
    public String getProtocolName() {
        return "Autoseeker";
    }
}