package com.jjenus.tracker.devicecomm.service;

import com.jjenus.tracker.devicecomm.application.DeviceDataProcessor;
import com.jjenus.tracker.devicecomm.domain.DeviceDataPacket;
import com.jjenus.tracker.shared.redis.RedisConnectionTracker;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpServer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ReactiveTcpServer {
    private static final Logger logger = LoggerFactory.getLogger(ReactiveTcpServer.class);
    private static final AtomicInteger CONNECTION_COUNTER = new AtomicInteger(0);

    private final DeviceDataProcessor deviceDataProcessor;
    private final RedisConnectionTracker connectionTracker;
    
    @Value("${tracking.tcp.server.port:8080}")
    private int tcpPort;
    
    @Value("${tracking.tcp.server.message-delimiter:#}")
    private String messageDelimiter;
    
    @Value("${tracking.tcp.server.max-message-length:1024}")
    private int maxMessageLength;
    
    @Value("${tracking.tcp.server.read-timeout:300}")
    private int readTimeoutSeconds;
    
    private Connection serverConnection;
    private volatile boolean running = false;

    public ReactiveTcpServer(DeviceDataProcessor deviceDataProcessor,
                            RedisConnectionTracker connectionTracker) {
        this.deviceDataProcessor = deviceDataProcessor;
        this.connectionTracker = connectionTracker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (running) {
            logger.warn("TCP Server is already running");
            return;
        }
        
        logger.info("Starting Reactive TCP Server on port {}", tcpPort);
        
        try {
            serverConnection = TcpServer.create()
                    .port(tcpPort)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .doOnConnection(this::handleNewConnection)
                    .doOnBind(bind -> logger.info("TCP Server binding to port {}", tcpPort))
                    .doOnBound(bound -> logger.info("TCP Server successfully bound"))
                    .doOnUnbound(unbound -> logger.info("TCP Server unbound"))
                    .wiretap("reactor.netty.tcp.TcpServer", LogLevel.DEBUG)
                    .handle((inbound, outbound) -> {
                        String connectionId = "conn-" + CONNECTION_COUNTER.incrementAndGet();
                        InetSocketAddress remoteAddress = (InetSocketAddress) inbound.address();
                        String clientIp = remoteAddress != null ? 
                            remoteAddress.getAddress().getHostAddress() : "unknown";
                        
                        // Get the Netty Connection object
                        Connection nettyConnection = inbound.context().channel().attr(Connection.class).get();
                        
                        // Initially deviceId is unknown - will be updated when we parse first message
                        String initialDeviceId = "unknown";
                        connectionTracker.registerConnection(connectionId, initialDeviceId, clientIp, nettyConnection);
                        
                        // Add timeout handler
                        inbound.withConnection(conn ->
                                conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS)));
                        
                        return inbound.receive()
                                .asString()
                                .transform(this::frameMessages)
                                .doOnNext(message -> processRawMessage(message, connectionId, clientIp))
                                .doOnError(error -> handleConnectionError(error, connectionId))
                                .doFinally(signal -> cleanupConnection(connectionId))
                                .then();
                    })
                    .bindNow(Duration.ofSeconds(30));
            
            running = true;
            logger.info("TCP Server started successfully on port {}", tcpPort);
            
        } catch (Exception e) {
            logger.error("Failed to start TCP Server on port {}", tcpPort, e);
            throw new RuntimeException("Failed to start TCP Server", e);
        }
    }

    private void handleNewConnection(Connection connection) {
        InetSocketAddress remoteAddress = (InetSocketAddress) connection.address();
        String clientIp = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
        int clientPort = remoteAddress != null ? remoteAddress.getPort() : 0;
        
        logger.info("New TCP connection from {}:{}", clientIp, clientPort);
        
        connection.onDispose(() -> {
            logger.info("Connection from {}:{} closed", clientIp, clientPort);
        });
    }

    private Flux<String> frameMessages(Flux<String> dataStream) {
        return Flux.create(sink -> {
            StringBuilder buffer = new StringBuilder();
            
            dataStream.subscribe(
                chunk -> {
                    buffer.append(chunk);
                    splitByDelimiter(buffer, sink);
                },
                sink::error,
                () -> {
                    if (buffer.length() > 0) {
                        sink.next(buffer.toString());
                    }
                    sink.complete();
                }
            );
        });
    }

    private void splitByDelimiter(StringBuilder buffer, Sinks.Many<String> sink) {
        int delimiterIndex;
        while ((delimiterIndex = buffer.indexOf(messageDelimiter)) != -1) {
            String completeMessage = buffer.substring(0, delimiterIndex + 1);
            buffer.delete(0, delimiterIndex + 1);
            sink.tryEmitNext(completeMessage);
        }
        
        if (buffer.length() > maxMessageLength) {
            logger.warn("Message buffer overflow ({} > {}), clearing buffer", 
                       buffer.length(), maxMessageLength);
            buffer.setLength(0);
        }
    }

    private void processRawMessage(String rawMessage, String connectionId, String clientIp) {
        try {
            logger.debug("Received message ({} chars) from connection {}", rawMessage.length(), connectionId);
            
            // Extract device ID from the raw message
            String deviceId = extractDeviceIdFromRawMessage(rawMessage);
            
            // Update connection tracker with actual device ID if we extracted it
            if (!"unknown".equals(deviceId)) {
                connectionTracker.updateConnectionDevice(connectionId, deviceId);
            }
            
            DeviceDataPacket packet = new DeviceDataPacket(
                deviceId,
                rawMessage,
                Instant.now(),
                clientIp
            );
            
            // Process asynchronously
            Mono.fromRunnable(() -> deviceDataProcessor.processDeviceData(packet))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    null,
                    error -> logger.error("Error processing device data for connection {}", connectionId, error)
                );
            
            // Update last seen in connection tracker
            connectionTracker.updateLastSeen(connectionId);
            
        } catch (Exception e) {
            logger.error("Error processing raw message from connection {}", connectionId, e);
        }
    }
    
    private String extractDeviceIdFromRawMessage(String rawMessage) {
        // Simple extraction - assumes format: *XX,DEVICE_ID,...
        try {
            if (rawMessage != null && rawMessage.startsWith("*")) {
                String[] parts = rawMessage.split(",", 3);
                if (parts.length >= 2) {
                    return parts[1].trim(); // Second part is device ID
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract device ID from message: {}", 
                       rawMessage.substring(0, Math.min(rawMessage.length(), 50)));
        }
        return "unknown";
    }

    private void handleConnectionError(Throwable error, String connectionId) {
        logger.warn("Connection error for {}: {}", connectionId, error.getMessage());
    }

    private void cleanupConnection(String connectionId) {
        connectionTracker.removeConnection(connectionId);
        logger.info("Cleaned up connection {}", connectionId);
    }

    public void stop() {
        if (!running) {
            logger.warn("TCP Server is not running");
            return;
        }
        
        logger.info("Stopping TCP Server...");
        
        if (serverConnection != null && !serverConnection.isDisposed()) {
            serverConnection.disposeNow();
        }
        
        running = false;
        logger.info("TCP Server stopped");
    }

    public int getActiveConnectionCount() {
        return connectionTracker.getActiveConnectionCount();
    }

    public Mono<Boolean> sendCommandToDevice(String deviceId, String command) {
        return Mono.fromCallable(() -> {
            try {
                // Get connection from Redis
                RedisConnectionTracker.ConnectionInfo connectionInfo = 
                    connectionTracker.getConnectionByDeviceId(deviceId);
                
                if (connectionInfo == null) {
                    logger.warn("No active connection found for device {}", deviceId);
                    return false;
                }
                
                // Get Netty connection
                reactor.netty.Connection nettyConnection = connectionInfo.getNettyConnection();
                if (nettyConnection == null || nettyConnection.isDisposed()) {
                    logger.warn("Connection for device {} is not available", deviceId);
                    // Clean up stale connection
                    connectionTracker.removeConnection(connectionInfo.getConnectionId());
                    return false;
                }
                
                // Send command through the connection
                return nettyConnection.outbound()
                    .sendString(Mono.just(command + messageDelimiter))
                    .then()
                    .timeout(Duration.ofSeconds(10))
                    .thenReturn(true)
                    .onErrorResume(e -> {
                        logger.warn("Failed to send command to device {}: {}", deviceId, e.getMessage());
                        return Mono.just(false);
                    })
                    .block(); // This is okay in fromCallable as it runs on boundedElastic
                    
            } catch (Exception e) {
                logger.error("Error sending command to device {}", deviceId, e);
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}