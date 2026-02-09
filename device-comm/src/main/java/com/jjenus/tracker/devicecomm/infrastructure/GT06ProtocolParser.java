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

public class GT06ProtocolParser implements ITrackerProtocolParser {
    private static final Pattern GT06_PATTERN = Pattern.compile("^\\*[A-Z]{2},[0-9]{10,20},(V[0-5]|HTBT|S20|D[0-9]+),.*#");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("ddMMyyHHmmss");
    private final Logger log = LoggerFactory.getLogger(GT06ProtocolParser.class);

    @Override
    public LocationPoint parse(String data) throws ProtocolParseException {
        try {
            if (!canParse(data)) {
                throw new ProtocolParseException("Not a valid GT06 protocol message");
            }

            // Remove * and #
            String cleanData = data.substring(1, data.length() - 1);
            String[] parts = cleanData.split(",");

            String deviceId = parts[1];
            String protocolType = parts[2]; // V1, V2, V3, V4, V5, HTBT

            // Create base metadata with device info
            Map<String, Object> baseMetadata = new HashMap<>();
            baseMetadata.put(LocationMetadataConstants.DEVICE_ID, deviceId);
            baseMetadata.put(LocationMetadataConstants.PROTOCOL_NAME, "GT06");
            baseMetadata.put(LocationMetadataConstants.PROTOCOL_TYPE, protocolType);

            // Handle command response packets
            if ("V4".equals(protocolType) && parts.length >= 4 && "S20".equals(parts[3])) {
                return parseCommandResponsePacket(parts, baseMetadata);
            }

            LocationPoint location = switch (protocolType) {
                case "V0" -> parseLoginPacket(parts, baseMetadata);
                case "V1", "V2", "V4" -> parseGPSPacket(parts, baseMetadata);
                case "V3" -> parseLBSPacket(parts, baseMetadata);
                case "V5" -> parseWifiPacket(parts, baseMetadata);
                case "HTBT" -> parseHeartbeatPacket(parts, baseMetadata);
                default -> parseGenericPacket(parts, baseMetadata);
            };

            return location;

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse GT06 data: " + e.getMessage());
        }
    }

    private LocationPoint parseCommandResponsePacket(String[] parts, Map<String, Object> baseMetadata) throws ProtocolParseException {
        try {
            if (parts.length < 17) {
                throw new ProtocolParseException("Incomplete command response packet");
            }

            String responseTimeStr = parts[5];
            String gpsTimeStr = parts[6];
            String validity = parts[7];
            String latStr = parts[8];
            String latDir = parts[9];
            String lonStr = parts[10];
            String lonDir = parts[11];
            String speedStr = parts[12];
            String directionStr = parts[13];
            String dateStr = parts[14];

            // Parse coordinates
            double latitude = parseDDMMtoDecimal(latStr);
            if ("S".equals(latDir)) latitude = -latitude;

            double longitude = parseDDMMtoDecimal(lonStr);
            if ("W".equals(lonDir)) longitude = -longitude;

            // Parse speed
            float speedKnots = parseFloatSafe(speedStr, 0.0f);
            float speedKmh = speedKnots * 1.852f;

            // Parse timestamp
            String[] dateAndStatus = extractDateAndStatus(dateStr);
            String cleanDateStr = dateAndStatus[0];
            String statusHex = dateAndStatus[1];

            Instant timestamp = parseTimestampSafe(cleanDateStr, gpsTimeStr, responseTimeStr);

            // Create metadata
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put(LocationMetadataConstants.VALIDITY, validity);
            metadata.put(LocationMetadataConstants.HEADING, parseHeading(directionStr));
            metadata.put(LocationMetadataConstants.RESPONSE_TIME, responseTimeStr);
            metadata.put(LocationMetadataConstants.GPS_TIME, gpsTimeStr);
            metadata.put(LocationMetadataConstants.PACKET_TYPE, "commandResponse");
            metadata.put(LocationMetadataConstants.COMMAND_CODE, parts[3]);
            metadata.put(LocationMetadataConstants.COMMAND_STATUS, parts[4]);

            // Add status bits
            if (!statusHex.isEmpty()) {
                metadata.put(LocationMetadataConstants.VEHICLE_STATUS_HEX, statusHex);
                Map<String, Boolean> statusBits = parseStatusBits(statusHex);
                metadata.putAll(statusBits);
            }

            // Add network information
            addNetworkInfo(metadata, parts, 15);

            // Add extra fields
            addExtraFields(metadata, parts, 19, "extra");

            log.debug("GT06 command response parsed with {} metadata entries", metadata.size());
            return new LocationPoint(latitude, longitude, speedKmh, timestamp, metadata);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse command response packet: " + e.getMessage());
        }
    }

    private LocationPoint parseGPSPacket(String[] parts, Map<String, Object> baseMetadata) throws ProtocolParseException {
        try {
            if (parts.length < 12) {
                throw new ProtocolParseException("Incomplete GPS packet");
            }

            String timeStr = parts[3];
            String validity = parts[4];
            String latStr = parts[5];
            String latDir = parts[6];
            String lonStr = parts[7];
            String lonDir = parts[8];
            String speedKnotStr = parts[9];
            String directionStr = parts[10];
            String dateStr = parts[11];

            // Parse coordinates
            double latitude = parseDDMMtoDecimal(latStr);
            if ("S".equals(latDir)) latitude = -latitude;

            double longitude = parseDDMMtoDecimal(lonStr);
            if ("W".equals(lonDir)) longitude = -longitude;

            // Parse speed
            float speedKnots = parseFloatSafe(speedKnotStr, 0.0f);
            float speedKmh = speedKnots * 1.852f;

            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);

            // Create metadata
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put(LocationMetadataConstants.VALIDITY, validity);
            metadata.put(LocationMetadataConstants.HEADING, parseHeading(directionStr));
            metadata.put(LocationMetadataConstants.TIME_STR, timeStr);
            metadata.put(LocationMetadataConstants.DATE_STR, dateStr);
            metadata.put(LocationMetadataConstants.PACKET_TYPE, "gps");

            // Parse status bits if available
            if (parts.length > 12) {
                String statusHex = parts[12];
                metadata.put(LocationMetadataConstants.VEHICLE_STATUS_HEX, statusHex);
                Map<String, Boolean> statusBits = parseStatusBits(statusHex);
                metadata.putAll(statusBits);
            }

            // Add extra fields
            addExtraFields(metadata, parts, 13, "extra");

            log.debug("GT06 GPS packet parsed with {} metadata entries", metadata.size());
            return new LocationPoint(latitude, longitude, speedKmh, timestamp, metadata);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse GPS packet: " + e.getMessage());
        }
    }

    private LocationPoint parseLBSPacket(String[] parts, Map<String, Object> baseMetadata) throws ProtocolParseException {
        try {
            if (parts.length < 9) {
                throw new ProtocolParseException("Incomplete LBS packet");
            }

            String timeStr = parts[3];
            String mccMnc = parts[4];
            String dateStr = parts[parts.length - 2];

            // Extract battery info
            String batteryHex = "0000";
            for (int i = 5; i < parts.length; i++) {
                if (parts[i].length() == 4 && parts[i].matches("[0-9A-Fa-f]{4}")) {
                    batteryHex = parts[i];
                    break;
                }
            }

            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);

            // Create metadata
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put(LocationMetadataConstants.PACKET_TYPE, "lbs");
            metadata.put(LocationMetadataConstants.MCC_MNC, mccMnc);
            metadata.put(LocationMetadataConstants.BATTERY_INFO_HEX, batteryHex);
            metadata.put(LocationMetadataConstants.TIME_STR, timeStr);
            metadata.put(LocationMetadataConstants.DATE_STR, dateStr);

            // Parse MCC/MNC
            if (mccMnc.length() >= 5) {
                metadata.put(LocationMetadataConstants.MCC, mccMnc.substring(0, 3));
                metadata.put(LocationMetadataConstants.MNC, mccMnc.substring(3, 5));
            }

            // Add all parts for debugging
            for (int i = 0; i < parts.length; i++) {
                metadata.put(LocationMetadataConstants.PART_PREFIX + "_" + i, parts[i]);
            }

            log.debug("GT06 LBS packet parsed with {} metadata entries", metadata.size());
            return new LocationPoint(0.0, 0.0, 0.0f, timestamp, metadata);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse LBS packet: " + e.getMessage());
        }
    }

    private LocationPoint parseWifiPacket(String[] parts, Map<String, Object> baseMetadata) throws ProtocolParseException {
        try {
            if (parts.length < 8) {
                throw new ProtocolParseException("Incomplete WIFI packet");
            }

            String timeStr = parts[3];
            String wifiCountStr = parts[4];
            String dateStr = parts[parts.length - 2];

            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);

            // Create metadata
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put(LocationMetadataConstants.PACKET_TYPE, "wifi");
            metadata.put(LocationMetadataConstants.WIFI_COUNT, wifiCountStr);
            metadata.put(LocationMetadataConstants.TIME_STR, timeStr);
            metadata.put(LocationMetadataConstants.DATE_STR, dateStr);

            // Parse WiFi APs if available
            try {
                int wifiCount = Integer.parseInt(wifiCountStr);
                int wifiStart = 5;
                for (int i = 0; i < wifiCount && (wifiStart + i) < parts.length - 2; i++) {
                    String wifiInfo = parts[wifiStart + i];
                    String[] wifiParts = wifiInfo.split(":");
                    if (wifiParts.length >= 2) {
                        metadata.put(LocationMetadataConstants.WIFI_MAC_PREFIX + "_" + i, wifiParts[0]);
                        metadata.put(LocationMetadataConstants.WIFI_SIGNAL_PREFIX + "_" + i, wifiParts[1]);
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("Failed to parse WiFi count: {}", wifiCountStr);
            }

            log.debug("GT06 WIFI packet parsed with {} metadata entries", metadata.size());
            return new LocationPoint(0.0, 0.0, 0.0f, timestamp, metadata);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse WIFI packet: " + e.getMessage());
        }
    }

    private LocationPoint parseHeartbeatPacket(String[] parts, Map<String, Object> baseMetadata) {
        Instant timestamp = Instant.now();

        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put(LocationMetadataConstants.PACKET_TYPE, "heartbeat");

        if (parts.length > 3) {
            metadata.put(LocationMetadataConstants.BATTERY_PERCENT, parts[3]);
        }

        log.debug("GT06 heartbeat packet parsed");
        return new LocationPoint(0.0, 0.0, 0.0f, timestamp, metadata);
    }

    private LocationPoint parseLoginPacket(String[] parts, Map<String, Object> baseMetadata) {
        Instant timestamp = Instant.now();

        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put(LocationMetadataConstants.PACKET_TYPE, "login");

        log.debug("GT06 login packet parsed");
        return new LocationPoint(0.0, 0.0, 0.0f, timestamp, metadata);
    }

    private LocationPoint parseGenericPacket(String[] parts, Map<String, Object> baseMetadata) {
        Instant timestamp = Instant.now();

        Map<String, Object> metadata = new HashMap<>(baseMetadata);
        metadata.put(LocationMetadataConstants.PACKET_TYPE, "generic");

        // Add all parts for debugging
        for (int i = 0; i < parts.length; i++) {
            metadata.put(LocationMetadataConstants.PART_PREFIX + "_" + i, parts[i]);
        }

        log.debug("GT06 generic packet parsed with {} parts", parts.length);
        return new LocationPoint(0.0, 0.0, 0.0f, timestamp, metadata);
    }

    // Helper methods

    private double parseDDMMtoDecimal(String ddmm) {
        try {
            double value = Double.parseDouble(ddmm);
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

    private String[] extractDateAndStatus(String dateStr) {
        String cleanDateStr = getCurrentDateString();
        String statusHex = "";

        if (dateStr != null && dateStr.length() >= 6 && dateStr.matches(".*[0-9]{6}.*")) {
            cleanDateStr = dateStr.substring(0, 6);
            if (dateStr.length() > 6) {
                statusHex = dateStr.substring(6);
            }
        }

        return new String[]{cleanDateStr, statusHex};
    }

    private Instant parseTimestampSafe(String dateStr, String gpsTimeStr, String fallbackTimeStr) {
        try {
            return parseDateTime(dateStr, gpsTimeStr);
        } catch (Exception e) {
            log.warn("Failed to parse GPS timestamp, using response time: {}", e.getMessage());
            return parseDateTime(dateStr, fallbackTimeStr);
        }
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
        return GT06_PATTERN.matcher(data).matches();
    }

    @Override
    public String buildFuelCutCommand(String deviceId) {
        try {
            String timeStr = getCurrentTimeString();
            String command = String.format("*HQ,%s,S20,%s,1,3#", deviceId, timeStr);
            return command;
        } catch (Exception e) {
            throw ProtocolException.commandBuildError("GT06", "fuel cut");
        }
    }

    @Override
    public String buildEngineOnCommand(String deviceId) {
        try {
            String timeStr = getCurrentTimeString();
            String command = String.format("*HQ,%s,S20,%s,1,0#", deviceId, timeStr);
            return command;
        } catch (Exception e) {
            throw ProtocolException.commandBuildError("GT06", "engine on");
        }
    }

    @Override
    public String getProtocolName() {
        return "GT06";
    }

    public Map<String, Boolean> parseStatusBits(String statusHex) {
        Map<String, Boolean> statusMap = new HashMap<>();

        try {
            if (statusHex == null || statusHex.length() != 8) {
                return statusMap;
            }

            long value = Long.parseLong(statusHex, 16);
            String binary = String.format("%32s", Long.toBinaryString(value)).replace(' ', '0');

            // Parse bits according to protocol (LSB first, 0 = active)
            // Byte 4 bits (bits 24-31)
            statusMap.put(LocationMetadataConstants.DOOR_OPEN, binary.charAt(31) == '0');
            statusMap.put(LocationMetadataConstants.OVERSPEED_ALARM, binary.charAt(30) == '0');
            statusMap.put(LocationMetadataConstants.FENCE_IN_ALARM, binary.charAt(28) == '0');
            statusMap.put(LocationMetadataConstants.FENCE_OUT_ALARM, binary.charAt(25) == '0');

            // Byte 3 bits (bits 16-23)
            statusMap.put(LocationMetadataConstants.GPS_SIGNAL, binary.charAt(22) == '0');
            statusMap.put(LocationMetadataConstants.ACC_OFF, binary.charAt(22) == '0');
            statusMap.put(LocationMetadataConstants.SOS_ALARM, binary.charAt(21) == '0');
            statusMap.put(LocationMetadataConstants.VIBRATION_ALARM, binary.charAt(19) == '0');
            statusMap.put(LocationMetadataConstants.LOW_BATTERY_ALARM, binary.charAt(18) == '0');

            // Byte 2 bits (bits 8-15)
            statusMap.put(LocationMetadataConstants.POWER_CUT_ALARM, binary.charAt(12) == '0');

            // Byte 1 bits (bits 0-7)
            statusMap.put(LocationMetadataConstants.VEHICLE_BATTERY_REMOVE_ALARM, binary.charAt(4) == '0');
            statusMap.put(LocationMetadataConstants.ANTI_TAMPER_ALARM, binary.charAt(3) == '0');
            statusMap.put(LocationMetadataConstants.CUT_OFF_OIL, binary.charAt(2) == '0');

        } catch (Exception e) {
            log.warn("Error parsing status bits: {}", e.getMessage());
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