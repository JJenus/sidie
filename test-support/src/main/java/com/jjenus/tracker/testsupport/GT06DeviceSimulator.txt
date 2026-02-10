package com.jjenus.tracker.testsupport;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class GT06DeviceSimulator {

    private final String host;
    private final int port;
    private final String imei;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private int gprsIntervalSeconds = 5;
    private int workingMode = 0; // 0: real-time, 1: power saving, 2: deep sleep

    private double lat = 22.675865;
    private double lon = 113.972065;
    private double speed = 0.0;
    private int direction = 0;
    private String validity = "A";

    private int batteryPercent = 100;
    private String vehicleStatus = "FFFFFBFF";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("ddMMyy");
    private static final Random RANDOM = new Random();

    public GT06DeviceSimulator(String host, int port, String imei) {
        this.host = host;
        this.port = port;
        this.imei = imei;
    }

    public void start() {
        if (running.getAndSet(true)) return;

        scheduler.scheduleWithFixedDelay(this::tryConnect, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::sendDataIfNeeded, 1, 1, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::simulateBehavior, 2, 2, TimeUnit.SECONDS);

        System.out.printf("[GT06 %s] Simulator started%n", imei);
    }

    public void stop() {
        running.set(false);
        connected.set(false);
        scheduler.shutdownNow();
        commandExecutor.shutdownNow();
        closeSocket();
        System.out.printf("[GT06 %s] Simulator stopped%n", imei);
    }

    private void tryConnect() {
        if (connected.get() || !running.get()) return;

        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            connected.set(true);
            System.out.printf("[GT06 %s] Connected to %s:%d%n", imei, host, port);

            // Send login
            sendData("*HQ," + imei + ",V0#");

            // Start command reader
            commandExecutor.submit(this::readCommandsLoop);

        } catch (IOException e) {
            System.out.printf("[GT06 %s] Connect failed: %s%n", imei, e.getMessage());
            closeSocket();
        }
    }

    private void closeSocket() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        connected.set(false);
    }

    private void readCommandsLoop() {
        StringBuilder buffer = new StringBuilder();
        while (running.get() && connected.get()) {
            try {
                int ch = in.read();
                if (ch == -1) {
                    connected.set(false);
                    break;
                }
                buffer.append((char) ch);
                if (ch == '#') {
                    String message = buffer.toString().trim();
                    buffer.setLength(0);
                    if (message.startsWith("*")) {
                        processCommand(message);
                    }
                }
            } catch (IOException e) {
                connected.set(false);
                break;
            }
        }
    }

    private void processCommand(String command) {
        System.out.printf("[SERVER → GT06 %s] %s%n", imei, command);

        try {
            String clean = command.substring(1, command.length() - 1);
            String[] parts = clean.split(",");

            if (parts.length < 3) return;

            String cmdCode = null;
            if (parts.length > 2) cmdCode = parts[2];
            if (cmdCode == null && parts.length > 3) cmdCode = parts[3];

            if (cmdCode != null) {
                switch (cmdCode) {
                    case "S1"  -> handleChangePassword(parts);
                    case "S2"  -> handleSetCenterNumber(parts);
                    case "S3"  -> handleSetAdminNumber(parts);
                    case "S18" -> handleSetAlarmMode(parts);
                    case "S19" -> handleAlarmTypeSetting(parts);
                    case "S20" -> handleRemoteDisableFuel(parts);
                    case "S21" -> handleSetGeoFence(parts);
                    case "S23" -> handleSetIpPort(parts);
                    case "S24" -> handleSetApn(parts);
                    case "S25" -> handleFactoryDefault(parts);
                    case "S26" -> handleReadDeviceState(parts);
                    case "S33" -> handleOverspeedSetting(parts);
                    case "S80" -> handleCheckLbs(parts);
                    case "D1"  -> handleSetGprsInterval(parts);
                    case "D2"  -> handleFastLocate(parts);
                    case "R1"  -> handleRestart(parts);
                    case "WKMD"-> handleChangeWorkingMode(parts);
                    case "SLP" -> handleSleepMode(parts);
                    case "V0"  -> handleLogin(parts);
                    case "HTBT"-> handleHeartbeat(parts);
                    default    -> System.out.printf("[!] Unknown GT06 command: %s%n", cmdCode);
                }
            }
        } catch (Exception e) {
            System.out.printf("[!] Command error: %s%n", e.getMessage());
        }
    }

    private void sendData(String data) {
        if (!connected.get()) return;
        try {
            if (!data.endsWith("#")) data += "#";
            out.print(data);
            out.flush();
            System.out.printf("[GT06 %s → SERVER] (%d bytes) %s%n", imei, data.length(), data);
        } catch (Exception e) {
            System.out.printf("[GT06 %s] Send failed: %s%n", imei, e.getMessage());
            connected.set(false);
        }
    }

    // ---------------- Command Handlers (minimal implementation – extend as needed) ----------------

    private void handleChangePassword(String[] parts)          { sendResponse(parts); }
    private void handleSetCenterNumber(String[] parts)         { sendResponse(parts); }
    private void handleSetAdminNumber(String[] parts)          { sendResponse(parts); }
    private void handleSetAlarmMode(String[] parts)            { sendResponse(parts); }
    private void handleAlarmTypeSetting(String[] parts)        { sendResponse(parts); }
    private void handleRemoteDisableFuel(String[] parts)       { sendResponse(parts); }
    private void handleSetGeoFence(String[] parts)             { sendResponse(parts); }
    private void handleSetIpPort(String[] parts)               { sendResponse(parts); }
    private void handleSetApn(String[] parts)                  { sendResponse(parts); }
    private void handleFactoryDefault(String[] parts)          { sendResponse(parts); }

    private void handleReadDeviceState(String[] parts) {
        String checkType = parts.length > 4 ? parts[4] : "0";
        String time = LocalDateTime.now().format(TIME_FMT);
        String resp;
        if ("0".equals(checkType)) {
            resp = "*HQ," + imei + ",V4,S26," + parts[3] + "," + time +
                   ",CMNET,,,13812341234,1,100," + gprsIntervalSeconds + ",8," + batteryPercent + "#";
        } else if ("1".equals(checkType)) {
            resp = "*HQ," + imei + ",V4,S26," + parts[3] + "," + time +
                   ",GW61D_ZDR_TK102_V2.6.2,2016/07/28 21:16#";
        } else {
            resp = "*HQ," + imei + ",V4,S26," + parts[3] + "," + time + ",Additional info#";
        }
        sendData(resp);
    }

    private void handleOverspeedSetting(String[] parts)        { sendResponse(parts); }
    private void handleCheckLbs(String[] parts)                { sendResponse(parts); }
    private void handleSetGprsInterval(String[] parts) {
        if (parts.length > 4) {
            gprsIntervalSeconds = Integer.parseInt(parts[4]);
            System.out.printf("[+] GPRS interval set to %d s%n", gprsIntervalSeconds);
        }
        sendResponse(parts);
    }
    private void handleFastLocate(String[] parts)              { sendResponse(parts); }
    private void handleRestart(String[] parts) {
        sendResponse(parts);
        System.out.println("[+] Restarting simulated device...");
        // Simulate restart by re-sending login after delay
        scheduler.schedule(() -> sendData("*HQ," + imei + ",V0#"), 2, TimeUnit.SECONDS);
    }
    private void handleChangeWorkingMode(String[] parts) {
        if (parts.length > 4) workingMode = Integer.parseInt(parts[4]);
        sendResponse(parts);
    }
    private void handleSleepMode(String[] parts)               { sendResponse(parts); }
    private void handleLogin(String[] parts)                   { sendData("*HQ," + imei + ",V0#"); }
    private void handleHeartbeat(String[] parts)               { sendData("*HQ," + imei + ",HTBT#"); }

    private void sendResponse(String[] originalParts) {
        sendResponse(originalParts, "DONE");
    }

    private void sendResponse(String[] originalParts, String status) {
        String time = LocalDateTime.now().format(TIME_FMT);
        String date = LocalDateTime.now().format(DATE_FMT);
        String latStr = formatCoord(lat);
        String lonStr = formatCoord(lon);

        StringBuilder sb = new StringBuilder("*HQ,").append(imei).append(",V4,")
                .append(originalParts[2]).append(",").append(status).append(",")
                .append(originalParts.length > 3 ? originalParts[3] : time).append(",")
                .append(time).append(",").append(validity).append(",")
                .append(latStr).append(",N,").append(lonStr).append(",E,")
                .append(String.format("%05.2f", speed)).append(",")
                .append(String.format("%03d", direction)).append(",")
                .append(date).append(",").append(vehicleStatus).append("#");

        sendData(sb.toString());
    }

    private String formatCoord(double coord) {
        int deg = (int) Math.abs(coord);
        double min = (Math.abs(coord) - deg) * 60;
        return String.format("%02d%07.4f", deg, min);
    }

    // ---------------- Sending & Simulation ----------------

    private void sendDataIfNeeded() {
        if (!connected.get() || !running.get()) return;

        if (workingMode == 0 && RANDOM.nextInt(1000) < (1000 / gprsIntervalSeconds)) {
            sendData(buildGpsData());
        } else if (workingMode == 1 && RANDOM.nextDouble() > 0.7) {
            updateLocationMovement();
            sendData(buildGpsData());
        }
        // Deep sleep: almost nothing

        if (RANDOM.nextDouble() > 0.8) {
            sendData("*HQ," + imei + ",HTBT#");
        }
    }

    private String buildGpsData() {
        String time = LocalDateTime.now().format(TIME_FMT);
        String date = LocalDateTime.now().format(DATE_FMT);
        String latStr = formatCoord(lat);
        String lonStr = formatCoord(lon);

        return String.format("*HQ,%s,V1,%s,%s,%s,N,%s,E,%.2f,%03d,%s,%s#",
                imei, time, validity, latStr, lonStr, speed, direction, date, vehicleStatus);
    }

    private void simulateBehavior() {
        if (workingMode == 0 || (workingMode == 1 && RANDOM.nextDouble() > 0.6)) {
            updateLocationMovement();
        }
    }

    private void updateLocationMovement() {
        lat += RANDOM.nextGaussian() * 0.0008;
        lon += RANDOM.nextGaussian() * 0.0008;
        speed = RANDOM.nextDouble() * 60;
        direction = RANDOM.nextInt(360);
        validity = "A";
    }

    // Getters for assertions in tests
    public boolean isConnected() { return connected.get(); }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getSpeed() { return speed; }
}