package com.jjenus.tracker.devicecomm.infrastructure;

import com.jjenus.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.jjenus.tracker.core.domain.LocationPoint;
import com.jjenus.tracker.devicecomm.exception.ProtocolException;
import com.jjenus.tracker.devicecomm.exception.ProtocolParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class GT06ProtocolParser implements ITrackerProtocolParser {
    private static final Pattern GT06_PATTERN = Pattern.compile("^\\*[A-Z]{2},[0-9]{15},.*#");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("ddMMyyHHmmss");
    
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
            
            switch (protocolType) {
                case "V0":
                    return parseLoginPacket(parts);
                case "V1":
                case "V2":
                case "V4":
                    return parseGPSPacket(parts);
                case "V3":
                    return parseLBSPacket(parts);
                case "V5":
                    return parseWifiPacket(parts);
                case "HTBT":
                    return parseHeartbeatPacket(parts);
                default:
                    throw new ProtocolParseException("Unknown protocol type: " + protocolType);
            }
            
        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse GT06 data: " + e.getMessage());
        }
    }
    
    private LocationPoint parseGPSPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *XX,IMEI,V1/V2/V4,HHMMSS,valid,latitude,N/S,longitude,E/W,speed,direction,DDMMYY,status#
            // Example: *HQ,865205030330012,V1,145452,A,2240.55181,N,11358.32389,E,0.00,0,100815,FFFFFBFF#
            
            if (parts.length < 12) {
                throw new ProtocolParseException("Incomplete GPS packet");
            }
            
            String imei = parts[1];
            String timeStr = parts[3];
            String validity = parts[4];
            String latStr = parts[5];
            String latDir = parts[6];
            String lonStr = parts[7];
            String lonDir = parts[8];
            String speedKnotStr = parts[9];
            String directionStr = parts[10];
            String dateStr = parts[11];
            
            // Parse latitude (DDMM.MMMMM format)
            double latitude = parseDDMMtoDecimal(latStr);
            if ("S".equals(latDir)) {
                latitude = -latitude;
            }
            
            // Parse longitude (DDDMM.MMMMM format)
            double longitude = parseDDMMtoDecimal(lonStr);
            if ("W".equals(lonDir)) {
                longitude = -longitude;
            }
            
            // Parse speed (knots to km/h)
            float speedKnots = Float.parseFloat(speedKnotStr);
            float speedKmh = speedKnots * 1.852f;
            
            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);
            
            // Data is only valid if validity is 'A'
            boolean isValid = "A".equals(validity);
            
            // For invalid GPS, we might still want to process it but with 0 speed
            if (!isValid) {
                speedKmh = 0.0f;
            }
            
            return new LocationPoint(latitude, longitude, speedKmh, timestamp);
            
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
            
            String imei = parts[1];
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
            
            // Parse timestamp (for LBS, we still need a timestamp)
            Instant timestamp = parseDateTime(dateStr, timeStr);
            
            // LBS doesn't have real GPS coordinates, so we return default with 0 speed
            // In production, you'd look up the cell tower location
            return new LocationPoint(0.0, 0.0, 0.0f, timestamp);
            
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
            String dateStr = parts[parts.length - 2];
            
            // Parse timestamp
            Instant timestamp = parseDateTime(dateStr, timeStr);
            
            // WIFI tracking doesn't have GPS coordinates by default
            return new LocationPoint(0.0, 0.0, 0.0f, timestamp);
            
        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse WIFI packet: " + e.getMessage());
        }
    }
    
    private LocationPoint parseHeartbeatPacket(String[] parts) throws ProtocolParseException {
        try {
            // Format: *XX,IMEI,HTBT# or *XX,IMEI,HTBT,battery_percent#
            
            String imei = parts[1];
            Instant timestamp = Instant.now();
            
            // Heartbeat doesn't have location, return current location or default
            // In your system, you might want to fetch last known location
            return new LocationPoint(0.0, 0.0, 0.0f, timestamp);
            
        } catch (Exception e) {
            throw new ProtocolParseException("Failed to parse heartbeat packet: " + e.getMessage());
        }
    }
    
    private LocationPoint parseLoginPacket(String[] parts) throws ProtocolParseException {
        // Login packet just announces device presence
        Instant timestamp = Instant.now();
        return new LocationPoint(0.0, 0.0, 0.0f, timestamp);
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
    
    private Instant parseDateTime(String dateStr, String timeStr) {
        try {
            String dateTimeStr = dateStr + timeStr;
            LocalDateTime ldt = LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            // If parsing fails, return current time
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
    public byte[] buildFuelCutCommand(String deviceId) {
        try {
            // GT06 fuel cut command format: *XX,IMEI,S20,HHMMSS,C,time1,time2,...#
            // From protocol: C=1 (static disable), time1=3 (3 seconds)
            
            String timeStr = getCurrentTimeString();
            String command = String.format("*HQ,%s,S20,%s,1,3#", deviceId, timeStr);
            return command.getBytes();
            
        } catch (Exception e) {
            throw ProtocolException.commandBuildError("GT06", "fuel cut");
        }
    }
    
    @Override
    public byte[] buildEngineOnCommand(String deviceId) {
        try {
            // GT06 fuel restore command: *XX,IMEI,S20,HHMMSS,1,0#
            
            String timeStr = getCurrentTimeString();
            String command = String.format("*HQ,%s,S20,%s,1,0#", deviceId, timeStr);
            return command.getBytes();
            
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
            statusMap.put("door_open", binary.charAt(31) == '0');          // bit 0
            statusMap.put("overspeed_alarm", binary.charAt(30) == '0');    // bit 2
            statusMap.put("fence_in_alarm", binary.charAt(28) == '0');     // bit 4
            statusMap.put("fence_out_alarm", binary.charAt(25) == '0');    // bit 7
            
            // Byte 3 bits (bits 16-23)
            statusMap.put("gps_status", binary.charAt(22) == '0');         // bit 2
            statusMap.put("acc_off", binary.charAt(22) == '0');           // bit 2 (same as GPS status?)
            statusMap.put("sos_alarm", binary.charAt(21) == '0');         // bit 11
            statusMap.put("vibration_alarm", binary.charAt(19) == '0');   // bit 13
            statusMap.put("low_battery_alarm", binary.charAt(18) == '0'); // bit 14
            
            // Byte 2 bits (bits 8-15)
            statusMap.put("power_cut_alarm", binary.charAt(12) == '0');   // bit 20
            
            // Byte 1 bits (bits 0-7)
            statusMap.put("vehicle_battery_remove_alarm", binary.charAt(4) == '0');  // bit 4
            statusMap.put("anti_tamper_alarm", binary.charAt(3) == '0');             // bit 5
            statusMap.put("cut_off_oil", binary.charAt(2) == '0');                   // bit 6
            
        } catch (Exception e) {
            // Log error but don't throw
            System.err.println("Error parsing status bits: " + e.getMessage());
        }
        
        return statusMap;
    }
}