package com.jjenus.tracker.shared.util;

/**
 * Comprehensive constants for location metadata keys used across the application.
 * These constants ensure consistent naming when parsing device data
 * and storing location information across GT06 and Autoseeker protocols.
 */
public class LocationMetadataConstants {

    // === DEVICE IDENTIFICATION ===
    public static final String DEVICE_ID = "deviceId";
    public static final String IMEI = "imei";
    public static final String MODEL = "model";
    public static final String FIRMWARE_VERSION = "firmwareVersion";
    public static final String SIM_NUMBER = "simNumber";
    public static final String PROTOCOL_NAME = "protocolName";

    // === BASIC LOCATION FIELDS ===
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String SPEED_KNOTS = "speedKnots";
    public static final String SPEED_KMH = "speedKmh";
    public static final String HEADING = "heading";
    public static final String ALTITUDE = "altitude";
    public static final String VALIDITY = "validity";
    public static final String ODOMETER_KM = "odometerKm";

    // === BATTERY INFORMATION ===
    public static final String BATTERY_VOLTAGE = "batteryVoltage";
    public static final String BATTERY_LEVEL = "batteryLevel";
    public static final String BATTERY_PERCENT = "batteryPercent";
    public static final String BATTERY_INFO_HEX = "batteryInfoHex";

    // === SIGNAL & NETWORK INFORMATION ===
    public static final String SIGNAL_STRENGTH = "signalStrength";
    public static final String GSM_SIGNAL = "gsmSignal";
    public static final String GPS_SIGNAL = "gpsSignal";
    public static final String MCC = "mcc";               // Mobile Country Code
    public static final String MNC = "mnc";               // Mobile Network Code
    public static final String LAC = "lac";               // Location Area Code
    public static final String CELL_ID = "cellId";        // Cell ID
    public static final String MCC_MNC = "mccMnc";        // Combined MCC+MNC

    // === VEHICLE STATUS FLAGS ===
    public static final String ACC_STATUS = "accStatus";
    public static final String ENGINE_STATUS = "engineStatus";
    public static final String FUEL_CUT_ACTIVE = "fuelCutActive";
    public static final String CUT_FUEL_ELECTRICITY = "cutFuelElectricity";
    public static final String CUT_OFF_OIL = "cutOffOil";
    public static final String DOOR_OPEN = "doorOpen";
    public static final String ACC_OFF = "accOff";
    public static final String ACC_CLOSE = "accClose";

    // === ALARM/ALERT FIELDS ===
    public static final String SOS_ALARM = "sosAlarm";
    public static final String VIBRATION_ALARM = "vibrationAlarm";
    public static final String LOW_BATTERY_ALARM = "lowBatteryAlarm";
    public static final String OVERSPEED_ALARM = "overspeedAlarm";
    public static final String FENCE_IN_ALARM = "fenceInAlarm";
    public static final String FENCE_OUT_ALARM = "fenceOutAlarm";
    public static final String SHAKE_ALARM = "shakeAlarm";
    public static final String POWER_CUT_ALARM = "powerCutAlarm";
    public static final String VEHICLE_BATTERY_REMOVE_ALARM = "vehicleBatteryRemoveAlarm";
    public static final String BATTERY_REMOVE_ALARM = "batteryRemoveAlarm";
    public static final String ANTI_TAMPER_ALARM = "antiTamperAlarm";
    public static final String SAVE_STATUS = "saveStatus";
    public static final String REMOVE_ALARM = "removeAlarm";
    public static final String SUPPLEMENTARY_DATA = "supplementaryData";

    // === PROTOCOL INFORMATION ===
    public static final String PROTOCOL_TYPE = "protocolType";    // V1, V2, V3, V4, V5, HTBT
    public static final String PACKET_TYPE = "packetType";        // gps, lbs, wifi, heartbeat, commandResponse, login
    public static final String VEHICLE_STATUS_HEX = "vehicleStatusHex"; // Raw hex status
    public static final String COMMAND_CODE = "commandCode";      // S20, D1, etc.
    public static final String COMMAND_STATUS = "commandStatus";  // DONE, etc.

    // === TIMESTAMP FIELDS ===
    public static final String RECORDED_AT = "recordedAt";
    public static final String GPS_TIME = "gpsTime";
    public static final String RESPONSE_TIME = "responseTime";
    public static final String DATE_STR = "dateStr";
    public static final String TIME_STR = "timeStr";

    // === CONNECTION INFORMATION ===
    public static final String CONNECTION_TYPE = "connectionType"; // TCP, UDP, HTTP
    public static final String IP_ADDRESS = "ipAddress";
    public static final String PORT = "port";
    public static final String SOURCE_IP = "sourceIp";

    // === GPS SPECIFIC FIELDS ===
    public static final String SATELLITE_COUNT = "satelliteCount";
    public static final String HDOP = "hdop";      // Horizontal Dilution of Precision
    public static final String VDOP = "vdop";      // Vertical Dilution of Precision
    public static final String PDOP = "pdop";      // Position Dilution of Precision

    // === WIFI INFORMATION ===
    public static final String WIFI_COUNT = "wifiCount";
    public static final String WIFI_MAC_PREFIX = "wifiMac";
    public static final String WIFI_SIGNAL_PREFIX = "wifiSignal";

    // === DEVICE SPECIFIC FIELDS (Autoseeker) ===
    public static final String SET_FENCE = "setFence";
    public static final String USE_BACKUP_BATTERY = "useBackupBattery";
    public static final String OPEN_LOCK = "openLock";

    // === DEBUGGING FIELDS ===
    public static final String EXTRA_PREFIX = "extra";
    public static final String PART_PREFIX = "part";
    public static final String RAW_DATA = "rawData";


    private LocationMetadataConstants() {
        throw new UnsupportedOperationException("Utility class - cannot be instantiated");
    }
}