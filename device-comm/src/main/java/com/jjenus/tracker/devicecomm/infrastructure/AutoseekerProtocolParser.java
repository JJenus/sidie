package com.jjenus.tracker.devicecomm.infrastructure;

import com.jjenus.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.jjenus.tracker.devicecomm.exception.ProtocolException;
import com.jjenus.tracker.devicecomm.exception.ProtocolParseException;
import com.jjenus.tracker.shared.util.LocationMetadataConstants;
import com.jjenus.tracker.shared.domain.LocationPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class AutoseekerProtocolParser implements ITrackerProtocolParser {

    private static final Pattern AUTOSEEKER_PATTERN = Pattern.compile(
            "^\\*[A-Z]{2},[0-9]{10},(V[0-5]|R12|D1|S20|SCF|S71|R7|LOCK|MILE),.*#"
    );

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMyyHHmmss");

    private final Logger log = LoggerFactory.getLogger(AutoseekerProtocolParser.class);

    @Override
    public LocationPoint parse(String data) throws ProtocolParseException {
        try {
            if (!canParse(data)) {
                throw new ProtocolParseException("Not a valid Autoseeker protocol message");
            }

            // Remove * and #
            String cleanData = data.substring(1, data.length() - 1);
            String[] parts = cleanData.split(",");

            String deviceId = parts[1];
            String protocolType = parts[2]; // V1, V4, etc.

            // Create base metadata with device info
            Map<String, Object> baseMetadata = new HashMap<>();
            baseMetadata.put(LocationMetadataConstants.DEVICE_ID, deviceId);
            baseMetadata.put(LocationMetadataConstants.PROTOCOL_NAME, "Autoseeker");
            baseMetadata.put(LocationMetadataConstants.PROTOCOL_TYPE, protocolType);

            return switch (protocolType) {
                case "V1" -> parseHeartPackPacket(parts, baseMetadata);
                case "V4" -> parseCommandResponsePacket(parts, baseMetadata);
                default -> parseGenericPacket(parts, baseMetadata);
            };

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse Autoseeker data: " + e.getMessage());
        }
    }

    private LocationPoint parseHeartPackPacket(String[] parts, Map<String, Object> baseMetadata) throws ProtocolParseException {
        try {
            if (parts.length < 17) {
                throw new ProtocolParseException("Incomplete heart pack packet");
            }

            String timeStr = parts[3];
            String validity = parts[4];
            String latStr = parts[5];
            String latDir = parts[6];
            String lonStr = parts[7];
            String lonDir = parts[8];
            String speedStr = parts[9];
            String directionStr = parts[10];
            String dateStr = parts[11];
            String vehicleStatus = parts[12];

            // Parse coordinates
            double latitude = parseDDMMtoDecimal(latStr);
            if ("S".equals(latDir)) latitude = -latitude;

            double longitude = parseDDMMtoDecimal(lonStr);
            if ("W".equals(lonDir)) longitude = -longitude;

            // Parse speed
            float speedKnots = parseFloatSafe(speedStr, 0.0f);
            float speedKmh = speedKnots * 1.852f;

            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);

            // Create metadata
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put(LocationMetadataConstants.VALIDITY, validity);
            metadata.put(LocationMetadataConstants.HEADING, parseHeading(directionStr));
            metadata.put(LocationMetadataConstants.VEHICLE_STATUS_HEX, vehicleStatus);
            metadata.put(LocationMetadataConstants.PACKET_TYPE, "heartbeat");
            metadata.put(LocationMetadataConstants.TIME_STR, timeStr);
            metadata.put(LocationMetadataConstants.DATE_STR, dateStr);

            // Parse vehicle status bits
            if (vehicleStatus != null && !vehicleStatus.isEmpty()) {
                Map<String, Boolean> statusBits = parseVehicleStatus(vehicleStatus);
                metadata.putAll(statusBits);
            }

            // Add network information
            addNetworkInfo(metadata, parts, 13);

            // Add GPS/GSM signal and voltage
            addSignalInfo(metadata, parts, 17);

            // Add extra fields
            addExtraFields(metadata, parts, 20, "extra");

            log.debug("Autoseeker heart pack parsed with {} metadata entries", metadata.size());
            return new LocationPoint(latitude, longitude, speedKmh, timestamp, metadata);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse heart pack packet: " + e.getMessage());
        }
    }

    private LocationPoint parseCommandResponsePacket(String[] parts, Map<String, Object> baseMetadata) throws ProtocolParseException {
        try {
            if (parts.length < 15) {
                throw new ProtocolParseException("Incomplete command response packet");
            }

            String commandCode = parts[3];
            String status = parts[4];

            int gpsDataStartIndex;
            String responseTime;
            String gpsTime;

            // Different response formats for different commands
            if ("D1".equals(commandCode)) {
                responseTime = parts[5];
                gpsTime = parts[6];
                gpsDataStartIndex = 7;
            } else if ("S20".equals(commandCode)) {
                responseTime = parts[4];
                gpsTime = parts[5];
                gpsDataStartIndex = 6;
            } else {
                responseTime = parts[4];
                gpsTime = parts[5];
                gpsDataStartIndex = 6;
            }

            // Create metadata
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put(LocationMetadataConstants.COMMAND_CODE, commandCode);
            metadata.put(LocationMetadataConstants.COMMAND_STATUS, status);
            metadata.put(LocationMetadataConstants.RESPONSE_TIME, responseTime);
            metadata.put(LocationMetadataConstants.GPS_TIME, gpsTime);
            metadata.put(LocationMetadataConstants.PACKET_TYPE, "commandResponse");

            // Parse GPS data if available
            if (parts.length > gpsDataStartIndex + 9) {
                return parseGPSFromCommandResponse(parts, gpsDataStartIndex, metadata);
            } else {
                // No GPS data in response
                log.debug("Command response without GPS data: cmd={}, status={}", commandCode, status);
                return new LocationPoint(0.0, 0.0, 0.0f, Instant.now(), metadata);
            }

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse command response packet: " + e.getMessage());
        }
    }

    private LocationPoint parseGPSFromCommandResponse(String[] parts, int startIndex, Map<String, Object> metadata) {
        String validity = parts[startIndex];
        String latStr = parts[startIndex + 1];
        String latDir = parts[startIndex + 2];
        String lonStr = parts[startIndex + 3];
        String lonDir = parts[startIndex + 4];
        String speedStr = parts[startIndex + 5];
        String directionStr = parts[startIndex + 6];
        String dateStatus = parts[startIndex + 7];

        // Parse coordinates
        double latitude = parseDDMMtoDecimal(latStr);
        if ("S".equals(latDir)) latitude = -latitude;

        double longitude = parseDDMMtoDecimal(lonStr);
        if ("W".equals(lonDir)) longitude = -longitude;

        // Parse speed
        float speedKnots = parseFloatSafe(speedStr, 0.0f);
        float speedKmh = speedKnots * 1.852f;

        // Extract date and status
        String[] dateAndStatus = extractDateAndStatus(dateStatus);
        String dateStr = dateAndStatus[0];
        String statusHex = dateAndStatus[1];

        // Parse timestamp
        Instant timestamp = parseDateTime(dateStr, metadata.get(LocationMetadataConstants.GPS_TIME).toString());

        // Add GPS metadata
        metadata.put(LocationMetadataConstants.VALIDITY, validity);
        metadata.put(LocationMetadataConstants.HEADING, parseHeading(directionStr));

        // Add status bits
        if (!statusHex.isEmpty()) {
            metadata.put(LocationMetadataConstants.VEHICLE_STATUS_HEX, statusHex);
            Map<String, Boolean> statusBits = parseVehicleStatus(statusHex);
            metadata.putAll(statusBits);
        }

        // Add network information
        addNetworkInfo(metadata, parts, startIndex + 10);

        log.debug("Autoseeker command response GPS parsed with {} metadata entries", metadata.size());
        return new LocationPoint(latitude, longitude, speedKmh, timestamp, metadata);
    }

    private LocationPoint parseGenericPacket(String[] parts, Map<String, Object> baseMetadata) {
        Instant timestamp = Instant.now();

        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put(LocationMetadataConstants.PACKET_TYPE, "generic");

        // Add all parts for debugging
        for (int i = 0; i < parts.length; i++) {
            metadata.put(LocationMetadataConstants.PART_PREFIX + "_" + i, parts[i]);
        }

        log.debug("Autoseeker generic packet parsed with {} parts", parts.length);
        return new LocationPoint(0.0, 0.0, 0.0f, timestamp, metadata);
    }

    // Helper methods

    private double parseDDMMtoDecimal(String ddmm) {
        try {
            String normalized = ddmm;
            if (ddmm.length() == 8 && !ddmm.contains(".")) {
                normalized = ddmm.substring(0, 4) + "." + ddmm.substring(4);
            }

            double value = Double.parseDouble(normalized);
            int degrees = (int)(value / 100);
            double minutes = value - (degrees * 100);
            return degrees + (minutes / 60.0);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse DDMM coordinate: {}", ddmm);
            return 0.0;
        }
    }

    private Float parseHeading(String directionStr) {
        try {
            return Float.parseFloat(directionStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Instant parseDateTime(String dateStr, String timeStr) {
        try {
            String dateTimeStr = dateStr + timeStr;
            LocalDateTime ldt = LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            log.warn("Failed to parse timestamp, using current time: {}", e.getMessage());
            return Instant.now();
        }
    }

    private float parseFloatSafe(String value, float defaultValue) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String[] extractDateAndStatus(String dateStatus) {
        String dateStr = getCurrentDateString();
        String statusHex = "";

        if (dateStatus != null && dateStatus.length() >= 6) {
            dateStr = dateStatus.substring(0, 6);
            if (dateStatus.length() > 6) {
                statusHex = dateStatus.substring(6);
            }
        }

        return new String[]{dateStr, statusHex};
    }

    private void addNetworkInfo(Map<String, Object> metadata, String[] parts, int startIndex) {
        if (parts.length > startIndex) {
            metadata.put(LocationMetadataConstants.MCC, parts[startIndex]);
        }
        if (parts.length > startIndex + 1) {
            metadata.put(LocationMetadataConstants.MNC, parts[startIndex + 1]);
        }
        if (parts.length > startIndex + 2) {
            metadata.put(LocationMetadataConstants.LAC, parts[startIndex + 2]);
        }
        if (parts.length > startIndex + 3) {
            metadata.put(LocationMetadataConstants.CELL_ID, parts[startIndex + 3]);
        }
    }

    private void addSignalInfo(Map<String, Object> metadata, String[] parts, int startIndex) {
        if (parts.length > startIndex) {
            try {
                metadata.put(LocationMetadataConstants.GPS_SIGNAL, Integer.parseInt(parts[startIndex]));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse GPS signal: {}", parts[startIndex]);
            }
        }
        if (parts.length > startIndex + 1) {
            try {
                metadata.put(LocationMetadataConstants.GSM_SIGNAL, Integer.parseInt(parts[startIndex + 1]));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse GSM signal: {}", parts[startIndex + 1]);
            }
        }
        if (parts.length > startIndex + 2) {
            try {
                metadata.put(LocationMetadataConstants.BATTERY_VOLTAGE, Integer.parseInt(parts[startIndex + 2]));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse voltage: {}", parts[startIndex + 2]);
            }
        }
    }

    private void addExtraFields(Map<String, Object> metadata, String[] parts, int startIndex, String prefix) {
        for (int i = startIndex; i < parts.length; i++) {
            metadata.put(prefix + "_" + (i - startIndex), parts[i]);
        }
    }

    @Override
    public boolean canParse(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        return AUTOSEEKER_PATTERN.matcher(data).matches();
    }

    @Override
    public String buildFuelCutCommand(String deviceId) {
        try {
            String timeStr = getCurrentTimeString();
            String command = String.format("*HQ,%s,S20,%s,1,3,10,3,5,5,3,5,3,5,3,5#", deviceId, timeStr);
            return command;
        } catch (Exception e) {
            throw ProtocolException.commandBuildError("Autoseeker", "fuel cut");
        }
    }

    @Override
    public String buildEngineOnCommand(String deviceId) {
        try {
            String timeStr = getCurrentTimeString();
            String command = String.format("*HQ,%s,S20,%s,0,0#", deviceId, timeStr);
            return command;
        } catch (Exception e) {
            throw ProtocolException.commandBuildError("Autoseeker", "engine on");
        }
    }

    @Override
    public String getProtocolName() {
        return "Autoseeker";
    }

    public Map<String, Boolean> parseVehicleStatus(String statusHex) {
        Map<String, Boolean> statusMap = new HashMap<>();

        try {
            if (statusHex == null || statusHex.length() < 8) {
                return statusMap;
            }

            long value = Long.parseLong(statusHex, 16);
            String binary = String.format("%32s", Long.toBinaryString(value)).replace(' ', '0');

            // Parse bits according to protocol (0 = active)
            // First byte (bits 24-31)
            statusMap.put(LocationMetadataConstants.SAVE_STATUS, binary.charAt(31) == '0');
            statusMap.put(LocationMetadataConstants.REMOVE_ALARM, binary.charAt(30) == '0');
            statusMap.put(LocationMetadataConstants.SUPPLEMENTARY_DATA, binary.charAt(29) == '0');
            statusMap.put(LocationMetadataConstants.CUT_FUEL_ELECTRICITY, binary.charAt(28) == '0');
            statusMap.put(LocationMetadataConstants.BATTERY_REMOVE_ALARM, binary.charAt(27) == '0');

            // Second byte (bits 16-23)
            statusMap.put(LocationMetadataConstants.SHAKE_ALARM, binary.charAt(23) == '0');
            statusMap.put(LocationMetadataConstants.SET_FENCE, binary.charAt(22) == '0');
            statusMap.put(LocationMetadataConstants.ACC_CLOSE, binary.charAt(21) == '0');

            // Third byte (bits 8-15)
            statusMap.put(LocationMetadataConstants.USE_BACKUP_BATTERY, binary.charAt(15) == '0');
            statusMap.put(LocationMetadataConstants.OPEN_LOCK, binary.charAt(14) == '0');

            // Fourth byte (bits 0-7)
            statusMap.put(LocationMetadataConstants.ENGINE_STATUS, binary.charAt(7) == '0');

        } catch (Exception e) {
            log.warn("Error parsing vehicle status: {}", e.getMessage());
        }

        return statusMap;
    }

    private String getCurrentTimeString() {
        return String.format("%02d%02d%02d",
                java.time.LocalTime.now().getHour(),
                java.time.LocalTime.now().getMinute(),
                java.time.LocalTime.now().getSecond());
    }

    private String getCurrentDateString() {
        return String.format("%02d%02d%02d",
                java.time.LocalDate.now().getDayOfMonth(),
                java.time.LocalDate.now().getMonthValue(),
                java.time.LocalDate.now().getYear() % 100);
    }
}