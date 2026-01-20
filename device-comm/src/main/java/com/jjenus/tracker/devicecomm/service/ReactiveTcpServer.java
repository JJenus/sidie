package com.jjenus.tracker.devicecomm.service;

import com.jjenus.tracker.devicecomm.application.DeviceDataProcessor;
import com.jjenus.tracker.devicecomm.domain.DeviceDataPacket;
import com.jjenus.tracker.shared.domain.ConnectionInfo;
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
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
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

    @Value("${tracking.tcp.server.port:8888}")
    private int tcpPort;

    @Value("${tracking.tcp.server.message-delimiter:#}")
    private String messageDelimiter;

    @Value("${tracking.tcp.server.max-message-length:1024}")
    private int maxMessageLength;

    @Value("${tracking.tcp.server.read-timeout:300}")
    private int readTimeoutSeconds;

    private DisposableServer server;
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
            server = TcpServer.create()
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

                        final String[] clientIp = new String[]{"unknown"};
                        final Connection[] nettyConnection = new Connection[1];

                        // CORRECT: Use withConnection (one of the 3 methods) to get the Connection
                        inbound.withConnection(conn -> {
                            nettyConnection[0] = conn;

                            // Get client IP from the Connection, not from inbound
                            InetSocketAddress remoteAddress = (InetSocketAddress) conn.address();
                            clientIp[0] = remoteAddress != null ?
                                    remoteAddress.getAddress().getHostAddress() : "unknown";

                            // Register connection in tracker
                            String initialDeviceId = "unknown";
                            connectionTracker.registerConnection(connectionId, initialDeviceId, clientIp[0], conn);

                            // Add timeout handler
                            conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS));

                            logger.debug("Connection {} established from {}", connectionId, clientIp[0]);
                        });

                        // CORRECT: Simplified message framing using bufferUntil
                        // Alternative: Use receive().asString().transform() with custom framing
                        return inbound.receive()
                                .asString()
                                .transform(this::frameMessagesWithDelimiter)
                                .doOnNext(message -> processRawMessage(message, connectionId, clientIp[0]))
                                .doOnError(error -> handleConnectionError(error, connectionId))
                                .doFinally(signal -> cleanupConnection(connectionId))
                                .then();
                    })
                    .bindNow();

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

    /**
     * Correct framing implementation using only Flux operators
     * No manual Flux.create needed
     */
    private Flux<String> frameMessagesWithDelimiter(Flux<String> dataStream) {
        return dataStream
                .windowUntil(s -> s.contains(messageDelimiter), true)
                .flatMap(window -> window.reduce(String::concat))
                .map(combined -> {
                    // Remove the delimiter for processing
                    logger.debug("Message: {}", combined);
                    if (combined.endsWith(messageDelimiter)) {
                        return combined.substring(0, combined.length() - 1);
                    }
                    return combined;
                });
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
                    rawMessage.trim(),
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

        if (server != null && !server.isDisposed()) {
            server.disposeNow();
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
                ConnectionInfo connectionInfo =
                        connectionTracker.getConnectionByDeviceId(deviceId);

                if (connectionInfo == null) {
                    logger.warn("No active connection found for device {}", deviceId);
                    return false;
                }

                // Get Netty connection
                Connection nettyConnection = connectionInfo.getNettyConnection();
                if (nettyConnection == null || nettyConnection.isDisposed()) {
                    logger.warn("Connection for device {} is not available", deviceId);
                    // Clean up stale connection
                    connectionTracker.removeConnection(connectionInfo.getConnectionId());
                    return false;
                }

                // Send command through the connection
                nettyConnection.outbound()
                        .sendString(Mono.just(command + messageDelimiter))
                        .then()
                        .block(Duration.ofSeconds(10));

                logger.debug("Command sent to device {}: {}", deviceId, command);
                return true;

            } catch (Exception e) {
                logger.error("Error sending command to device {}", deviceId, e);
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}