// src/test/java/com/jjenus/tracker/testsupport/AutoseekerDeviceSimulator.java

package com.jjenus.tracker.testsupport;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class AutoseekerDeviceSimulator {

    private final String host;
    private final int port;
    private final String deviceId;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private int uploadIntervalSeconds = 10;
    private int accStatus = 1; // 1 = ON
    private boolean fuelCut = false;
    private boolean fenceEnabled = false;

    private double lat = 22.582122;
    private double lon = 113.906633;
    private double speed = 0.0;
    private int direction = 0;
    private String validity = "A";

    private String vehicleStatus = "F7FFBBFF";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("ddMMyy");
    private static final Random RANDOM = new Random();

    public AutoseekerDeviceSimulator(String host, int port, String deviceId) {
        this.host = host;
        this.port = port;
        this.deviceId = deviceId;
    }

    public void start() {
        if (running.get()) return;
        running.set(true);

        scheduler.scheduleWithFixedDelay(this::tryConnect, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::sendHeartPackIfConnected, 1, 1, TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(this::simulateMovementAndAlarms, 2, 2, TimeUnit.SECONDS);

        System.out.printf("[AUTOSEEKER %s] Simulator started%n", deviceId);
    }

    public void stop() {
        running.set(false);
        connected.set(false);
        scheduler.shutdownNow();
        commandExecutor.shutdownNow();
        closeSocket();
        System.out.printf("[AUTOSEEKER %s] Simulator stopped%n", deviceId);
    }

    private void tryConnect() {
        if (connected.get() || !running.get()) return;

        try {
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            connected.set(true);
            System.out.printf("[AUTOSEEKER %s] Connected to %s:%d%n", deviceId, host, port);

            // Start command reader thread
            commandExecutor.submit(this::readCommandsLoop);

        } catch (IOException e) {
            System.out.printf("[AUTOSEEKER %s] Connect failed: %s%n", deviceId, e.getMessage());
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
        System.out.printf("[SERVER → %s] %s%n", deviceId, command);

        try {
            String clean = command.substring(1, command.length() - 1); // remove * and #
            String[] parts = clean.split(",");

            if (parts.length < 3) return;

            String cmdCode = parts[2];

            switch (cmdCode) {
                case "D1" -> handleSetUploadInterval(parts);
                case "S20" -> handleCutFuel(parts);
                case "SCF" -> handleSetFence(parts);
                case "S71" -> handleSetNumber(parts);
                case "R7" -> handleCleanAlarm(parts);
                case "LOCK" -> handleLockUnlock(parts);
                case "MILE" -> handleSetMileage(parts);
                case "R12" -> handleHeartbeatConfirmation(parts);
                default -> System.out.printf("[!] Unknown command: %s%n", cmdCode);
            }
        } catch (Exception e) {
            System.out.printf("[!] Command processing error: %s%n", e.getMessage());
        }
    }

    // ---------------- Command Handlers ----------------

    private void handleSetUploadInterval(String[] parts) {
        if (parts.length > 4) {
            uploadIntervalSeconds = Integer.parseInt(parts[4]);
            if (parts.length > 5) accStatus = Integer.parseInt(parts[5]);
            System.out.printf("[+] Upload interval set to %ds (ACC=%d)%n", uploadIntervalSeconds, accStatus);
        }
        sendResponse(parts, "DONE");
    }

    private void handleCutFuel(String[] parts) {
        if (parts.length > 5 && "0".equals(parts[5])) {
            fuelCut = false;
            System.out.println("[+] Fuel restored / engine ON");
        } else {
            fuelCut = true;
            System.out.printf("[+] Fuel cut activated: %s%n", Arrays.toString(parts));
        }
        sendResponse(parts, "OK");
    }

    private void handleSetFence(String[] parts) {
        fenceEnabled = parts.length > 4 && !"0".equals(parts[4]);
        System.out.printf("[+] Fence %s%n", fenceEnabled ? "enabled" : "disabled");
        sendResponse(parts);
    }

    private void handleSetNumber(String[] parts) { /* implement if needed */ sendResponse(parts); }
    private void handleCleanAlarm(String[] parts) { /* implement if needed */ sendResponse(parts); }
    private void handleLockUnlock(String[] parts) { /* implement if needed */ sendResponse(parts); }
    private void handleSetMileage(String[] parts) { /* implement if needed */ sendResponse(parts); }
    private void handleHeartbeatConfirmation(String[] parts) {
        System.out.println("[+] Heartbeat confirmation received");
        // usually no response needed
    }

    private void sendResponse(String[] originalParts, String status) {
        if (!connected.get()) return;

        String nowTime = LocalDateTime.now().format(TIME_FMT);
        String nowDate = LocalDateTime.now().format(DATE_FMT);

        StringBuilder sb = new StringBuilder("*HQ,").append(deviceId).append(",V4,")
                .append(originalParts[2]).append(",");

        if ("D1".equals(originalParts[2])) {
            sb.append(originalParts[4]).append(",65535,");
        } else if ("S20".equals(originalParts[2])) {
            sb.append(status).append(",");
        }

        sb.append(originalParts[3]).append(",").append(nowTime).append(",")
                .append(validity).append(",")
                .append(formatCoord(lat)).append(",N,")
                .append(formatCoord(lon)).append(",E,")
                .append(String.format("%05.2f", speed)).append(",")
                .append(String.format("%03d", direction)).append(",")
                .append(nowDate).append(",F7FFBBFF,460,00,10342,3721#");

        out.print(sb.toString());
        out.flush();

        System.out.printf("[AUTOSEEKER → SERVER] %s%n", sb);
    }

    private String formatCoord(double coord) {
        int deg = (int) coord;
        double min = (coord - deg) * 60;
        if (coord < 0) deg = -deg; // handle negative
        return String.format("%02d%07.4f", Math.abs(deg), min); // adjust format per protocol
    }

    // ---------------- Sending Logic ----------------

    private void sendHeartPackIfConnected() {
        if (!connected.get() || !running.get()) return;

        if (RANDOM.nextInt(100) < (1000 / uploadIntervalSeconds)) { // approx timing
            String heart = buildHeartPack();
            out.print(heart);
            out.flush();
            System.out.printf("[AUTOSEEKER → SERVER] %s%n", heart);
        }
    }

    private String buildHeartPack() {
        String nowTime = LocalDateTime.now().format(TIME_FMT);
        String nowDate = LocalDateTime.now().format(DATE_FMT);

        String latStr = formatCoord(lat);
        String lonStr = formatCoord(lon);

        String mcc = "460", mnc = "00", lac = "10342", cell = String.valueOf(RANDOM.nextInt(2000) + 3000);
        int gpsSig = RANDOM.nextInt(11) + 5;
        int gsmSig = RANDOM.nextInt(11) + 20;
        int voltage = RANDOM.nextInt(6) + 25;

        return String.format("*HQ,%s,V1,%s,%s,%s,N,%s,E,%.2f,%03d,%s,%s,%s,%s,%s,%s,%s,%d#",
                deviceId, nowTime, validity, latStr, lonStr, speed, direction, nowDate,
                vehicleStatus, mcc, mnc, lac, cell, gpsSig, gsmSig, voltage);
    }

    private void simulateMovementAndAlarms() {
        // Movement
        if (RANDOM.nextDouble() > 0.3) {
            lat += RANDOM.nextGaussian() * 0.0005;
            lon += RANDOM.nextGaussian() * 0.0005;
            speed = RANDOM.nextDouble() * 80;
            direction = RANDOM.nextInt(360);
            validity = "A";
        } else {
            speed = 0;
        }

        // Random alarm ~5%
        if (RANDOM.nextDouble() > 0.95 && connected.get()) {
            String alarm = buildAlarmPack();
            out.print(alarm);
            out.flush();
            System.out.printf("[!] Alarm sent: %s%n", alarm);
        }
    }

    private String buildAlarmPack() {
        // Similar to heart but with alarm status
        String nowTime = LocalDateTime.now().format(TIME_FMT);
        String nowDate = LocalDateTime.now().format(DATE_FMT);
        String latStr = formatCoord(lat);
        String lonStr = formatCoord(lon);

        return String.format("*HQ,%s,V1,%s,%s,%s,N,%s,E,%.2f,%03d,%s,FBFBBFF,460,00,10342,4283,10,25,128#",
                deviceId, nowTime, validity, latStr, lonStr, speed, direction, nowDate);
    }

    // Getters for testing assertions
    public boolean isConnected() { return connected.get(); }
    public double getCurrentLat() { return lat; }
    public double getCurrentLon() { return lon; }
    public double getCurrentSpeed() { return speed; }
}