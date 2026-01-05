// device-comm/src/main/java/com/jjenus/tracker/devicecomm/service/ReactiveTcpServer.java
package com.jjenus.tracker.devicecomm.service;

import com.jjenus.tracker.devicecomm.application.DeviceDataProcessor;
import com.jjenus.tracker.devicecomm.domain.DeviceDataPacket;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpServer;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ReactiveTcpServer {
    private static final Logger logger = LoggerFactory.getLogger(ReactiveTcpServer.class);
    private static final AtomicInteger CONNECTION_COUNTER = new AtomicInteger(0);

    private final DeviceDataProcessor deviceDataProcessor;
    private final ConcurrentHashMap<String, DeviceConnection> activeConnections = new ConcurrentHashMap<>();

    @Value("${tracking.tcp.server.port:8080}")
    private int tcpPort;

    @Value("${tracking.tcp.server.message-delimiter:#}")
    private String messageDelimiter;

    @Value("${tracking.tcp.server.max-message-length:1024}")
    private int maxMessageLength;

    private Connection serverConnection;

    public ReactiveTcpServer(DeviceDataProcessor deviceDataProcessor) {
        this.deviceDataProcessor = deviceDataProcessor;
    }

    public void start() {
        logger.info("Starting Reactive TCP Server on port {}", tcpPort);

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
                    // Create a connection context
                    String connectionId = "conn-" + CONNECTION_COUNTER.incrementAndGet();
                    DeviceConnection deviceConn = new DeviceConnection(connectionId, inbound);
                    activeConnections.put(connectionId, deviceConn);

                    // Add timeout handler
                    inbound.withConnection(conn ->
                            conn.addHandlerLast(new ReadTimeoutHandler(300, TimeUnit.SECONDS)));

                    // Get client IP
                    InetSocketAddress remoteAddress = (InetSocketAddress) inbound.address();
                    String clientIp = remoteAddress != null ?
                            remoteAddress.getAddress().getHostAddress() : "unknown";
                    deviceConn.setClientIp(clientIp);

                    // Handle incoming data with message framing
                    return inbound.receive()
                            .asString()  // Convert ByteBuf to String
                            .transform(this::frameMessages)  // Frame messages by delimiter
                            .doOnNext(message -> processRawMessage(message, deviceConn))
                            .doOnError(error -> handleConnectionError(error, deviceConn))
                            .doFinally(signal -> cleanupConnection(deviceConn))
                            .then();
                })
                .bindNow(Duration.ofSeconds(30));

        logger.info("TCP Server started successfully on port {}", tcpPort);
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
     * Message framing: Split stream by delimiter
     * Devices may send multiple messages in one packet or split across packets
     */
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
                        // Process any remaining data
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
            // Extract complete message (including delimiter)
            String completeMessage = buffer.substring(0, delimiterIndex + 1);
            buffer.delete(0, delimiterIndex + 1);

            sink.tryEmitNext(completeMessage);
        }

        // Check for buffer overflow
        if (buffer.length() > maxMessageLength) {
            logger.warn("Message buffer overflow ({} > {}), clearing buffer",
                    buffer.length(), maxMessageLength);
            buffer.setLength(0);
        }
    }

    /**
     * Process raw message - NO PARSING HERE!
     * Just pass raw bytes to DeviceDataProcessor
     */
    private void processRawMessage(String rawMessage, DeviceConnection deviceConn) {
        try {
            logger.debug("Received message ({} chars)", rawMessage.length());

            // Create packet with raw bytes
            DeviceDataPacket packet = new DeviceDataPacket(
                    null, // Device ID will be extracted by parser
                    rawMessage.getBytes(),
                    Instant.now(),
                    deviceConn.getClientIp()
            );

            // Process asynchronously (parser will determine protocol and extract device ID)
            Mono.fromRunnable(() -> deviceDataProcessor.processDeviceData(packet))
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            null,
                            error -> logger.error("Error processing device data", error)
                    );

            // Update connection last seen
            deviceConn.updateLastSeen();

        } catch (Exception e) {
            logger.error("Error processing raw message", e);
        }
    }

    private void handleConnectionError(Throwable error, DeviceConnection deviceConn) {
        logger.warn("Connection error for {}: {}",
                deviceConn.getConnectionId(), error.getMessage());
    }

    private void cleanupConnection(DeviceConnection deviceConn) {
        activeConnections.remove(deviceConn.getConnectionId());
        logger.info("Cleaned up connection {}", deviceConn.getConnectionId());
    }

    public void stop() {
        if (serverConnection != null && !serverConnection.isDisposed()) {
            logger.info("Stopping TCP Server...");
            serverConnection.disposeNow();
            activeConnections.clear();
            logger.info("TCP Server stopped");
        }
    }

    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    public Mono<Boolean> sendCommandToDevice(String deviceId, byte[] command) {
        return Flux.fromIterable(activeConnections.values())
                .filter(conn -> deviceId.equals(conn.getDeviceId()))
                .next()
                .flatMap(conn -> {
                    // Send command through the connection
                    return conn.getConnection()
                            .outbound()
                            .sendByteArray(Mono.just(command))
                            .then()
                            .thenReturn(true)
                            .timeout(Duration.ofSeconds(10))
                            .onErrorResume(e -> {
                                logger.warn("Failed to send command to device {}: {}", deviceId, e.getMessage());
                                return Mono.just(false);
                            });
                })
                .defaultIfEmpty(false);
    }

    // Connection tracking class
    private static class DeviceConnection {
        private final String connectionId;
        private final Connection connection;
        private final Instant connectedAt;
        private volatile Instant lastSeen;
        private volatile String deviceId;
        private volatile String clientIp;

        public DeviceConnection(String connectionId, Connection connection) {
            this.connectionId = connectionId;
            this.connection = connection;
            this.connectedAt = Instant.now();
            this.lastSeen = Instant.now();
        }

        public void updateLastSeen() {
            this.lastSeen = Instant.now();
        }

        // Getters and setters
        public String getConnectionId() { return connectionId; }
        public Connection getConnection() { return connection; }
        public Instant getConnectedAt() { return connectedAt; }
        public Instant getLastSeen() { return lastSeen; }
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    }
}