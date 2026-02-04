package com.jjenus.tracker.notification.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VehicleTrackingWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(VehicleTrackingWebSocketHandler.class);
    
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    
    public VehicleTrackingWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = extractUserId(session);
        if (userId != null) {
            userSessions.put(userId, session);
            logger.info("WebSocket connection established for user: {}", userId);
            
            // Send connection acknowledgment
            sendMessage(session, createConnectionAck(userId));
        } else {
            logger.warn("WebSocket connection established without user ID");
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = extractUserId(session);
        if (userId == null) {
            logger.warn("Received message from unauthenticated session");
            return;
        }
        
        try {
            String payload = message.getPayload();
            logger.debug("Received WebSocket message from user {}: {}", userId, payload);
            
            // Handle different message types
            handleClientMessage(userId, payload, session);
            
        } catch (Exception e) {
            logger.error("Error handling WebSocket message from user {}", userId, e);
            sendError(session, "Failed to process message");
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = extractUserId(session);
        if (userId != null) {
            userSessions.remove(userId);
            logger.info("WebSocket connection closed for user: {} with status: {}", userId, status);
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = extractUserId(session);
        logger.error("WebSocket transport error for user {}", userId, exception);
        
        if (userId != null) {
            userSessions.remove(userId);
        }
        
        session.close(CloseStatus.SERVER_ERROR);
    }
    
    /**
     * Send alert notification to specific user
     */
    public void sendAlertNotification(String userId, Object notification) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(notification);
                sendMessage(session, json);
                logger.debug("Sent notification to user {}: {}", userId, notification);
            } catch (IOException e) {
                logger.error("Failed to send notification to user {}", userId, e);
            }
        } else {
            logger.debug("User {} is not connected via WebSocket", userId);
        }
    }
    
    /**
     * Broadcast notification to all connected users
     */
    public void broadcastNotification(Object notification) {
        userSessions.forEach((userId, session) -> {
            if (session.isOpen()) {
                try {
                    String json = objectMapper.writeValueAsString(notification);
                    sendMessage(session, json);
                } catch (IOException e) {
                    logger.error("Failed to broadcast to user {}", userId, e);
                }
            }
        });
    }
    
    /**
     * Check if user is connected
     */
    public boolean isUserConnected(String userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }
    
    /**
     * Get connected user IDs
     */
    public int getConnectedUsersCount() {
        return userSessions.size();
    }
    
    private String extractUserId(WebSocketSession session) {
        // Extract from session attributes (set during authentication)
        Object userIdObj = session.getAttributes().get("userId");
        if (userIdObj != null) {
            return userIdObj.toString();
        }
        
        // Extract from query parameters
        String query = session.getUri().getQuery();
        if (query != null && query.contains("userId=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("userId=")) {
                    return param.substring("userId=".length());
                }
            }
        }
        
        return null;
    }
    
    private void handleClientMessage(String userId, String payload, WebSocketSession session) throws IOException {
        // Parse and handle different message types
        try {
            Map<String, Object> message = objectMapper.readValue(payload, Map.class);
            String type = (String) message.get("type");
            
            switch (type) {
                case "PING":
                    handlePing(userId, session);
                    break;
                case "SUBSCRIBE":
                    handleSubscribe(userId, message, session);
                    break;
                case "UNSUBSCRIBE":
                    handleUnsubscribe(userId, message, session);
                    break;
                default:
                    logger.warn("Unknown message type from user {}: {}", userId, type);
                    sendError(session, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            logger.error("Failed to parse message from user {}", userId, e);
            sendError(session, "Invalid message format");
        }
    }
    
    private void handlePing(String userId, WebSocketSession session) throws IOException {
        Map<String, Object> response = Map.of(
            "type", "PONG",
            "timestamp", System.currentTimeMillis(),
            "userId", userId
        );
        sendMessage(session, objectMapper.writeValueAsString(response));
    }
    
    private void handleSubscribe(String userId, Map<String, Object> message, WebSocketSession session) throws IOException {
        String topic = (String) message.get("topic");
        logger.info("User {} subscribed to topic: {}", userId, topic);
        
        Map<String, Object> response = Map.of(
            "type", "SUBSCRIBED",
            "topic", topic,
            "success", true
        );
        sendMessage(session, objectMapper.writeValueAsString(response));
    }
    
    private void handleUnsubscribe(String userId, Map<String, Object> message, WebSocketSession session) throws IOException {
        String topic = (String) message.get("topic");
        logger.info("User {} unsubscribed from topic: {}", userId, topic);
        
        Map<String, Object> response = Map.of(
            "type", "UNSUBSCRIBED",
            "topic", topic,
            "success", true
        );
        sendMessage(session, objectMapper.writeValueAsString(response));
    }
    
    private void sendMessage(WebSocketSession session, String message) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        }
    }
    
    private void sendError(WebSocketSession session, String error) throws IOException {
        Map<String, Object> errorResponse = Map.of(
            "type", "ERROR",
            "message", error,
            "timestamp", System.currentTimeMillis()
        );
        sendMessage(session, objectMapper.writeValueAsString(errorResponse));
    }
    
    private String createConnectionAck(String userId) {
        try {
            Map<String, Object> ack = Map.of(
                "type", "CONNECTED",
                "userId", userId,
                "timestamp", System.currentTimeMillis(),
                "message", "WebSocket connection established successfully"
            );
            return objectMapper.writeValueAsString(ack);
        } catch (Exception e) {
            logger.error("Failed to create connection ACK", e);
            return "{\"type\":\"CONNECTED\",\"userId\":\"" + userId + "\"}";
        }
    }
}
