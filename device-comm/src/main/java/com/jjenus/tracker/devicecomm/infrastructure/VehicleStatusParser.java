package com.jjenus.tracker.devicecomm.infrastructure;

import java.util.HashMap;
import java.util.Map;

public class VehicleStatusParser {
    
    public static Map<String, Object> parseGT06Status(String statusHex) {
        Map<String, Object> status = new HashMap<>();
        
        if (statusHex == null || statusHex.length() != 8) {
            return status;
        }
        
        try {
            // Parse 4-byte status field
            long value = Long.parseLong(statusHex, 16);
            String binary = String.format("%32s", Long.toBinaryString(value)).replace(' ', '0');
            
            // Byte 4 (bits 24-31)
            parseByte4(status, binary.substring(24, 32));
            
            // Byte 3 (bits 16-23)
            parseByte3(status, binary.substring(16, 24));
            
            // Byte 2 (bits 8-15)
            parseByte2(status, binary.substring(8, 16));
            
            // Byte 1 (bits 0-7)
            parseByte1(status, binary.substring(0, 8));
            
        } catch (Exception e) {
            status.put("parse_error", e.getMessage());
        }
        
        return status;
    }
    
    private static void parseByte1(Map<String, Object> status, String byteStr) {
        // Bit 0: GPS Status (0=located, 1=not located)
        boolean gpsLocated = byteStr.charAt(7) == '0';
        status.put("gps_located", gpsLocated);
        
        // Bit 1: Vehicle security condition
        boolean securityActive = byteStr.charAt(6) == '0';
        status.put("security_active", securityActive);
        
        // Bit 2: ACC off
        boolean accOff = byteStr.charAt(5) == '0';
        status.put("acc_off", accOff);
        
        // Bit 3: SOS Alarm
        boolean sosAlarm = byteStr.charAt(4) == '0';
        status.put("sos_alarm", sosAlarm);
        
        // Bit 4: Vibration Alarm
        boolean vibrationAlarm = byteStr.charAt(3) == '0';
        status.put("vibration_alarm", vibrationAlarm);
        
        // Bit 5: Low Battery Alarm
        boolean lowBatteryAlarm = byteStr.charAt(2) == '0';
        status.put("low_battery_alarm", lowBatteryAlarm);
    }
    
    private static void parseByte2(Map<String, Object> status, String byteStr) {
        // Bit 0: Power cut-off alarm
        boolean powerCutAlarm = byteStr.charAt(7) == '0';
        status.put("power_cut_alarm", powerCutAlarm);
        
        // Bit 1: Device powered by backup battery
        boolean backupBattery = byteStr.charAt(6) == '0';
        status.put("backup_battery", backupBattery);
    }
    
    private static void parseByte3(Map<String, Object> status, String byteStr) {
        // Bit 0: Anti-tamper alarm
        boolean antiTamperAlarm = byteStr.charAt(7) == '0';
        status.put("anti_tamper_alarm", antiTamperAlarm);
        
        // Bit 1: Cut off oil condition
        boolean oilCutOff = byteStr.charAt(6) == '0';
        status.put("oil_cut_off", oilCutOff);
        
        // Bit 2: Vehicle battery remove condition alarm
        boolean batteryRemoved = byteStr.charAt(5) == '0';
        status.put("battery_removed", batteryRemoved);
    }
    
    private static void parseByte4(Map<String, Object> status, String byteStr) {
        // Bit 0: Door open
        boolean doorOpen = byteStr.charAt(7) == '0';
        status.put("door_open", doorOpen);
        
        // Bit 2: Overspeeding alarm
        boolean overspeedAlarm = byteStr.charAt(5) == '0';
        status.put("overspeed_alarm", overspeedAlarm);
        
        // Bit 4: Fence-in alarm
        boolean fenceInAlarm = byteStr.charAt(3) == '0';
        status.put("fence_in_alarm", fenceInAlarm);
        
        // Bit 7: Fence-out alarm
        boolean fenceOutAlarm = byteStr.charAt(0) == '0';
        status.put("fence_out_alarm", fenceOutAlarm);
    }
    
    public static boolean isAlarmActive(Map<String, Object> status) {
        return (boolean) status.getOrDefault("sos_alarm", false) ||
               (boolean) status.getOrDefault("vibration_alarm", false) ||
               (boolean) status.getOrDefault("low_battery_alarm", false) ||
               (boolean) status.getOrDefault("power_cut_alarm", false) ||
               (boolean) status.getOrDefault("anti_tamper_alarm", false) ||
               (boolean) status.getOrDefault("overspeed_alarm", false) ||
               (boolean) status.getOrDefault("fence_in_alarm", false) ||
               (boolean) status.getOrDefault("fence_out_alarm", false);
    }
}