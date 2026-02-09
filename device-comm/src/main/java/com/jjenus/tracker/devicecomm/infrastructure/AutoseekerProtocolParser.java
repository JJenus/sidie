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

            String protocolType = parts[2]; // V1, V4, etc.

            return switch (protocolType) {
                case "V1" -> parseHeartPackPacket(parts);
                case "V4" -> parseCommandResponsePacket(parts);
                default -> parseGenericPacket(parts);
            };

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse Autoseeker data: " + e.getMessage());
        }
    }

    private LocationPoint parseHeartPackPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#
            if (parts.length < 17) {
                throw new ProtocolParseException("Incomplete heart pack packet");
            }

            String deviceId = parts[1];
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

            // Parse latitude (DDMM.MMMM format)
            double latitude = parseDDMMtoDecimal(latStr);
            if ("S".equals(latDir)) {
                latitude = -latitude;
            }

            // Parse longitude (DDDMM.MMMM format)
            double longitude = parseDDMMtoDecimal(lonStr);
            if ("W".equals(lonDir)) {
                longitude = -longitude;
            }

            // Parse speed (knots to km/h)
            float speedKnots = Float.parseFloat(speedStr);
            float speedKmh = speedKnots * 1.852f;

            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);

            // Data is only valid if validity is 'A'
            boolean isValid = "A".equals(validity);
            if (!isValid) {
                speedKmh = 0.0f;
                log.debug("GPS data not valid (validity={})", validity);
            }

            // Create metadata map and populate with all extra fields
            Map<String, Object> metadata = new HashMap<>();

            // Add basic fields to metadata
            metadata.put("validity", validity);
            metadata.put("heading", parseHeading(directionStr));
            metadata.put("vehicleStatus", vehicleStatus);
            metadata.put("protocol_type", "V1");

            // Parse vehicle status bits if available
            if (vehicleStatus != null && !vehicleStatus.isEmpty()) {
                Map<String, Boolean> statusBits = parseVehicleStatus(vehicleStatus);
                metadata.putAll(statusBits);
            }

            // Add network information to metadata
            if (parts.length > 13) {
                metadata.put("mcc", parts[13]);
                metadata.put("mnc", parts[14]);
                metadata.put("lac", parts[15]);
                metadata.put("cellId", parts[16]);
            }

            // Add GPS/GSM signal and voltage to metadata
            if (parts.length > 19) {
                try {
                    metadata.put("gpsSignal", Integer.parseInt(parts[17]));
                    metadata.put("gsmSignal", Integer.parseInt(parts[18]));
                    metadata.put("voltage", Integer.parseInt(parts[19]));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse signal/voltage data: {}", e.getMessage());
                }
            }

            // Add any additional fields to metadata
            for (int i = 20; i < parts.length; i++) {
                metadata.put("extra_" + (i - 20), parts[i]);
            }

            log.debug("Parsed Autoseeker location with {} metadata entries", metadata.size());

            return new LocationPoint(latitude, longitude, speedKmh, timestamp, metadata);

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse heart pack packet: " + e.getMessage());
        }
    }

    private LocationPoint parseCommandResponsePacket(String[] parts) throws ProtocolParseException {
        try {
            // Format varies by command, e.g.:
            // *HQ,8168000005,V4,D1,30,65535,062108,062025,A,2235.0086,N,11354.3668,E,000.00,000,160716,FFFFBBFF,460,00,10342,3721#
            // *HQ,8168000005,V4,S20,DONE,061158,061116,A,2235.0086,N,11354.3668,E,000.00,000,160716F7FFBBFF,460,00,10342,3721#
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
                // D1 format: V4,D1,30,65535,062108,062025,A,...
                responseTime = parts[5];
                gpsTime = parts[6];
                gpsDataStartIndex = 7;
            } else if ("S20".equals(commandCode)) {
                // S20 format: V4,S20,DONE,061158,061116,A,...
                responseTime = parts[4];  // DONE (but actually it's status)
                gpsTime = parts[5];
                gpsDataStartIndex = 6;
            } else {
                // Default format for other commands
                responseTime = parts[4];
                gpsTime = parts[5];
                gpsDataStartIndex = 6;
            }

            // Create metadata map
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("command_code", commandCode);
            metadata.put("command_status", status);
            metadata.put("response_time", responseTime);
            metadata.put("gps_time", gpsTime);
            metadata.put("protocol_type", "V4");

            // Parse GPS data if available
            if (parts.length > gpsDataStartIndex + 9) {
                String validity = parts[gpsDataStartIndex];
                String latStr = parts[gpsDataStartIndex + 1];
                String latDir = parts[gpsDataStartIndex + 2];
                String lonStr = parts[gpsDataStartIndex + 3];
                String lonDir = parts[gpsDataStartIndex + 4];
                String speedStr = parts[gpsDataStartIndex + 5];
                String directionStr = parts[gpsDataStartIndex + 6];
                String dateStatus = parts[gpsDataStartIndex + 7];

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
                float speedKnots = Float.parseFloat(speedStr);
                float speedKmh = speedKnots * 1.852f;

                // Extract date from combined field
                String dateStr;
                String statusHex = "";
                if (dateStatus.length() >= 6) {
                    dateStr = dateStatus.substring(0, 6);
                    if (dateStatus.length() > 6) {
                        statusHex = dateStatus.substring(6);
                        metadata.put("vehicleStatus", statusHex);
                        // Parse status bits
                        Map<String, Boolean> statusBits = parseVehicleStatus(statusHex);
                        metadata.putAll(statusBits);
                    }
                } else {
                    dateStr = getCurrentDateString();
                }

                // Parse timestamp
                Instant timestamp = parseDateTime(dateStr, gpsTime);

                // Add more metadata
                metadata.put("validity", validity);
                metadata.put("heading", parseHeading(directionStr));

                // Add network information if available
                if (parts.length > gpsDataStartIndex + 10) {
                    int networkStart = gpsDataStartIndex + 10;
                    if (parts.length > networkStart) metadata.put("mcc", parts[networkStart]);
                    if (parts.length > networkStart + 1) metadata.put("mnc", parts[networkStart + 1]);
                    if (parts.length > networkStart + 2) metadata.put("lac", parts[networkStart + 2]);
                    if (parts.length > networkStart + 3) metadata.put("cellId", parts[networkStart + 3]);
                }

                log.debug("Command response parsed with {} metadata entries", metadata.size());

                return new LocationPoint(latitude, longitude, speedKmh, timestamp, metadata);
            } else {
                // No GPS data in response
                log.debug("Command response without GPS data: cmd={}, status={}", commandCode, status);
                return new LocationPoint(0.0, 0.0, 0.0f, Instant.now(), metadata);
            }

        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse command response packet: " + e.getMessage());
        }
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

            // Convert hex to binary (32 bits)
            long value = Long.parseLong(statusHex, 16);
            String binary = String.format("%32s", Long.toBinaryString(value)).replace(' ', '0');

            // Parse bits according to protocol (0 = active)
            // Based on Appendix 1 table in the PDF

            // First byte (bits 24-31)
            statusMap.put("saveStatus", binary.charAt(31) == '0');          // bit 0
            statusMap.put("removeAlarm", binary.charAt(30) == '0');         // bit 1
            statusMap.put("supplementaryData", binary.charAt(29) == '0');   // bit 2
            statusMap.put("cutFuelElectricity", binary.charAt(28) == '0');  // bit 3
            statusMap.put("batteryRemoveAlarm", binary.charAt(27) == '0');  // bit 4

            // Second byte (bits 16-23)
            statusMap.put("shakeAlarm", binary.charAt(23) == '0');          // bit 8
            statusMap.put("setFence", binary.charAt(22) == '0');            // bit 9
            statusMap.put("accClose", binary.charAt(21) == '0');            // bit 10

            // Third byte (bits 8-15)
            statusMap.put("useBackupBattery", binary.charAt(15) == '0');    // bit 16
            statusMap.put("openLock", binary.charAt(14) == '0');            // bit 17

            // Fourth byte (bits 0-7)
            statusMap.put("engineStatus", binary.charAt(7) == '0');         // bit 24

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