package com.jjenus.tracker.devicecomm.infrastructure;

import com.jjenus.tracker.devicecomm.application.DeviceDataProcessor;
import com.jjenus.tracker.devicecomm.domain.DeviceDataPacket;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketServer {
    private final int port;
    private final DeviceDataProcessor dataProcessor;
    private final ExecutorService executor;
    private ServerSocket serverSocket;
    private volatile boolean running;
    
    public SocketServer(int port, DeviceDataProcessor dataProcessor) {
        this.port = port;
        this.dataProcessor = dataProcessor;
        this.executor = Executors.newCachedThreadPool();
    }
    
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Socket server started on port " + port);
        
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        }
    }
    
    private void handleClient(Socket clientSocket) {
        try {
            String clientIp = clientSocket.getInetAddress().getHostAddress();
            System.out.println("Client connected: " + clientIp);
            
            byte[] buffer = new byte[1024];
            int bytesRead;
            
            while (!clientSocket.isClosed() && 
                   (bytesRead = clientSocket.getInputStream().read(buffer)) != -1) {
                
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);
                
                String deviceId = extractDeviceId(data, clientIp);
                
                DeviceDataPacket packet = new DeviceDataPacket(
                    deviceId,
                    data,
                    Instant.now(),
                    clientIp
                );
                
                dataProcessor.processDeviceData(packet);
            }
            
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    private String extractDeviceId(byte[] data, String clientIp) {
        return "DEV_" + clientIp.replace(".", "_") + "_" + System.currentTimeMillis();
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // Ignore
        }
        executor.shutdown();
    }
}
