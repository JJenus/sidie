package com.jjenus.tracker.devicecomm.infrastructure;

import com.jjenus.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.jjenus.tracker.devicecomm.exception.ProtocolException;
import com.jjenus.tracker.devicecomm.exception.ProtocolParseException;
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

            String protocolType = parts[2]; // V1, V2, V3, V4, V5, HTBT

            // Handle command response packets
            if ("V4".equals(protocolType) && parts.length >= 4 && "S20".equals(parts[3])) {
                return parseCommandResponsePacket(parts);
            }

            return switch (protocolType) {
                case "V0" -> parseLoginPacket(parts);
                case "V1", "V2", "V4" -> parseGPSPacket(parts);
                case "V3" -> parseLBSPacket(parts);
                case "V5" -> parseWifiPacket(parts);
                case "HTBT" -> parseHeartbeatPacket(parts);
                default -> parseGenericPacket(parts);
            };

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse GT06 data: " + e.getMessage());
        }
    }

    private LocationPoint parseCommandResponsePacket(String[] parts) throws ProtocolParseException {
        try {
            // Format for S20 command response:
            // *HQ,IMEI,V4,S20,DONE,HHMMSS,response_time,A,latitude,N,longitude,E,speed,direction,DDMMYY,status,...
            // Example: *HQ,8168000005,V4,S20,DONE,061158,061116,A,2235.0086,N,11354.3668,E,000.00,000,160716F7FFBBFF,460,00
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

            // Parse latitude
            double latitude = parseDDMMtoDecimal(latStr);
            if ("S".equals(latDir)) {
                latitude = -latitude;
            }

            // Parse longitude
            double longitude = parseDDMMtoDecimal(lonStr);
            if ("W".equals(lonDir)) {
                longitude = -longitude;
            }

            // Parse speed
            float speedKnots = 0.0f;
            try {
                speedKnots = Float.parseFloat(speedStr);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse speed: {}", speedStr);
            }
            float speedKmh = speedKnots * 1.852f;

            // Parse timestamp
            String cleanDateStr = dateStr;
            String statusHex = "";
            if (dateStr.length() >= 6 && dateStr.matches(".*[0-9]{6}.*")) {
                cleanDateStr = dateStr.substring(0, 6);
                if (dateStr.length() > 6) {
                    statusHex = dateStr.substring(6);
                }
            } else {
                cleanDateStr = getCurrentDateString();
            }

            Instant timestamp;
            try {
                timestamp = parseDateTime(cleanDateStr, gpsTimeStr);
            } catch (Exception e) {
                log.warn("Failed to parse timestamp, using response time: {}", e.getMessage());
                timestamp = parseDateTime(cleanDateStr, responseTimeStr);
            }

            // Create metadata map
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("validity", validity);
            metadata.put("heading", parseHeading(directionStr));
            metadata.put("response_time", responseTimeStr);
            metadata.put("gps_time", gpsTimeStr);
            metadata.put("protocol_type", "V4");
            metadata.put("command_code", "S20");

            // Parse status bits if available
            if (!statusHex.isEmpty()) {
                metadata.put("vehicleStatus", statusHex);
                Map<String, Boolean> statusBits = parseStatusBits(statusHex);
                metadata.putAll(statusBits);
            }

            // Add network information
            if (parts.length > 15) {
                metadata.put("mcc", parts[15]);
                metadata.put("mnc", parts[16]);
            }

            if (parts.length > 17) {
                metadata.put("lac", parts[17]);
                metadata.put("cellId", parts[18]);
            }

            // Add any additional fields
            for (int i = 19; i < parts.length; i++) {
                metadata.put("extra_" + (i - 19), parts[i]);
            }

            return new LocationPoint(latitude, longitude, speedKmh, timestamp, metadata);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse command response packet: " + e.getMessage());
        }
    }

    private LocationPoint parseGPSPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *XX,IMEI,V1/V2/V4,HHMMSS,valid,latitude,N/S,longitude,E/W,speed,direction,DDMMYY,status#
            // Example: *HQ,865205030330012,V1,145452,A,2240.55181,N,11358.32389,E,0.00,0,100815,FFFFFBFF#
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

            // Parse latitude
            double latitude = parseDDMMtoDecimal(latStr);
            if ("S".equals(latDir)) {
                latitude = -latitude;
            }

            // Parse longitude
            double longitude = parseDDMMtoDecimal(lonStr);
            if ("W".equals(lonDir)) {
                longitude = -longitude;
            }

            // Parse speed
            float speedKnots = Float.parseFloat(speedKnotStr);
            float speedKmh = speedKnots * 1.852f;

            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);

            // Create metadata map
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("validity", validity);
            metadata.put("heading", parseHeading(directionStr));
            metadata.put("protocol_type", parts[2]);

            // Parse status bits if available
            if (parts.length > 12) {
                String statusHex = parts[12];
                metadata.put("vehicleStatus", statusHex);
                Map<String, Boolean> statusBits = parseStatusBits(statusHex);
                metadata.putAll(statusBits);
            }

            // Add any additional fields to metadata
            for (int i = 13; i < parts.length; i++) {
                metadata.put("extra_" + (i - 13), parts[i]);
            }

            return new LocationPoint(latitude, longitude, speedKmh, timestamp, metadata);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse GPS packet: " + e.getMessage());
        }
    }

    private LocationPoint parseLBSPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *XX,IMEI,V3,HHMMSS,base_info,battery_info,failure_info,cont,DDMMYY,status#
            // Example: *HQ,865205030330012,V3,000201,46000,07,...#
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

            // Create metadata map
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("protocol_type", "V3");
            metadata.put("packet_type", "lbs");
            metadata.put("mcc_mnc", mccMnc);
            metadata.put("battery_info", batteryHex);

            // Parse MCC/MNC
            if (mccMnc.length() >= 5) {
                metadata.put("mcc", mccMnc.substring(0, 3));
                metadata.put("mnc", mccMnc.substring(3, 5));
            }

            // Add all parts as metadata for debugging
            for (int i = 0; i < parts.length; i++) {
                metadata.put("part_" + i, parts[i]);
            }

            return new LocationPoint(0.0, 0.0, 0.0f, timestamp, metadata);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse LBS packet: " + e.getMessage());
        }
    }

    private LocationPoint parseWifiPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *XX,IMEI,V5,HHMMSS,wifi_count,wifi_info...,battery_info,DDMMYY,status#
            if (parts.length < 8) {
                throw new ProtocolParseException("Incomplete WIFI packet");
            }

            String timeStr = parts[3];
            String wifiCountStr = parts[4];
            String dateStr = parts[parts.length - 2];

            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);

            // Create metadata map
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("protocol_type", "V5");
            metadata.put("packet_type", "wifi");
            metadata.put("wifi_count", wifiCountStr);

            // Parse WiFi APs if available
            try {
                int wifiCount = Integer.parseInt(wifiCountStr);
                int wifiStart = 5;
                for (int i = 0; i < wifiCount && (wifiStart + i) < parts.length - 2; i++) {
                    String wifiInfo = parts[wifiStart + i];
                    String[] wifiParts = wifiInfo.split(":");
                    if (wifiParts.length >= 2) {
                        metadata.put("wifi_mac_" + i, wifiParts[0]);
                        metadata.put("wifi_signal_" + i, wifiParts[1]);
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("Failed to parse WiFi count: {}", wifiCountStr);
            }

            return new LocationPoint(0.0, 0.0, 0.0f, timestamp, metadata);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse WIFI packet: " + e.getMessage());
        }
    }

    private LocationPoint parseHeartbeatPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *XX,IMEI,HTBT# or *XX,IMEI,HTBT,battery_percent#
            Instant timestamp = Instant.now();

            // Create metadata map
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("protocol_type", "HTBT");
            metadata.put("packet_type", "heartbeat");

            if (parts.length > 3) {
                metadata.put("battery_percent", parts[3]);
            }

            return new LocationPoint(0.0, 0.0, 0.0f, timestamp, metadata);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse heartbeat packet: " + e.getMessage());
        }
    }

    private LocationPoint parseLoginPacket(String[] parts) throws ProtocolParseException {
        // Login packet just announces device presence
        Instant timestamp = Instant.now();

        // Create metadata map
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("protocol_type", "V0");
        metadata.put("packet_type", "login");

        return new LocationPoint(0.0, 0.0, 0.0f, timestamp, metadata);
    }

    private LocationPoint parseGenericPacket(String[] parts) {
        // Create metadata map for generic packets
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("protocol_type", parts[2]);
        metadata.put("packet_type", "generic");

        // Add all parts as metadata for debugging
        for (int i = 0; i < parts.length; i++) {
            metadata.put("part_" + i, parts[i]);
        }

        return new LocationPoint(0.0, 0.0, 0.0f, Instant.now(), metadata);
    }

    private double parseDDMMtoDecimal(String ddmm) {
        try {
            double value = Double.parseDouble(ddmm);
            int degrees = (int)(value / 100);
            double minutes = value - (degrees * 100);
            return degrees + (minutes / 60.0);
        } catch (NumberFormatException e) {
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
            return Instant.now();
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

    private String getCurrentTimeString() {
        return String.format("%02d%02d%02d",
                java.time.LocalTime.now().getHour(),
                java.time.LocalTime.now().getMinute(),
                java.time.LocalTime.now().getSecond());
    }

    @Override
    public String getProtocolName() {
        return "GT06";
    }

    // Additional utility method to parse vehicle status bits
    public Map<String, Boolean> parseStatusBits(String statusHex) {
        Map<String, Boolean> statusMap = new HashMap<>();

        try {
            if (statusHex == null || statusHex.length() != 8) {
                return statusMap;
            }

            // Convert hex to binary (32 bits)
            long value = Long.parseLong(statusHex, 16);
            String binary = String.format("%32s", Long.toBinaryString(value)).replace(' ', '0');

            // Parse bits according to protocol (LSB first, 0 = active)
            // Byte 4 bits (bits 24-31)
            statusMap.put("door_open", binary.charAt(31) == '0');
            statusMap.put("overspeed_alarm", binary.charAt(30) == '0');
            statusMap.put("fence_in_alarm", binary.charAt(28) == '0');
            statusMap.put("fence_out_alarm", binary.charAt(25) == '0');

            // Byte 3 bits (bits 16-23)
            statusMap.put("gps_status", binary.charAt(22) == '0');
            statusMap.put("acc_off", binary.charAt(22) == '0');
            statusMap.put("sos_alarm", binary.charAt(21) == '0');
            statusMap.put("vibration_alarm", binary.charAt(19) == '0');
            statusMap.put("low_battery_alarm", binary.charAt(18) == '0');

            // Byte 2 bits (bits 8-15)
            statusMap.put("power_cut_alarm", binary.charAt(12) == '0');

            // Byte 1 bits (bits 0-7)
            statusMap.put("vehicle_battery_remove_alarm", binary.charAt(4) == '0');
            statusMap.put("anti_tamper_alarm", binary.charAt(3) == '0');
            statusMap.put("cut_off_oil", binary.charAt(2) == '0');

        } catch (Exception e) {
            log.warn("Error parsing status bits: {}", e.getMessage());
        }

        return statusMap;
    }

    private String getCurrentDateString() {
        return String.format("%02d%02d%02d",
                java.time.LocalDate.now().getDayOfMonth(),
                java.time.LocalDate.now().getMonthValue(),
                java.time.LocalDate.now().getYear() % 100);
    }
}