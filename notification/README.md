# Notification Module

## Overview
The notification module handles all alert delivery and notification management for the Sidie tracking platform. It follows Domain-Driven Design (DDD) principles and Event-Driven Architecture (EDA).

## Architecture

### Layers
- **Domain**: Core business entities (Notification, NotificationTemplate, NotificationPreference)
- **Application**: Use cases and business logic (NotificationOrchestrator, NotificationDispatcher)
- **API**: REST controllers and DTOs
- **Infrastructure**: Data access (repositories), WebSocket handlers, external service integrations

### Key Components
1. **NotificationOrchestrator**: Coordinates notification creation and delivery
2. **NotificationDispatcher**: Routes notifications to appropriate delivery services
3. **WebSocketNotificationService**: Real-time browser notifications
4. **EmailNotificationService**: Email delivery
5. **VehicleTrackingWebSocketHandler**: Manages WebSocket connections

## API Endpoints

### Notifications
- `GET /api/notifications` - List notifications with filtering
- `GET /api/notifications/{id}` - Get notification by ID
- `POST /api/notifications/{id}/read` - Mark as read
- `DELETE /api/notifications/{id}` - Delete notification

### Preferences
- `GET /api/notifications/preferences/{userId}` - Get user preferences
- `PUT /api/notifications/preferences/{userId}` - Update preferences

### Templates
- `GET /api/notifications/templates` - List templates
- `POST /api/notifications/templates` - Create template
- `PUT /api/notifications/templates/{id}` - Update template
- `DELETE /api/notifications/templates/{id}` - Delete template

## WebSocket
- Endpoint: `/ws/notifications`
- Protocol: SockJS fallback supported
- Authentication: User ID required in connection

## Event Flow
1. Alerting module publishes `AlertRaisedEvent`
2. NotificationEventHandler receives the event
3. NotificationOrchestrator processes the alert
4. Notifications created based on user preferences
5. NotificationDispatcher sends via appropriate channels

## Configuration
See `src/main/resources/application.yaml` for configuration options.

## Database Schema
- `notifications`: Stores all notifications
- `notification_templates`: Template definitions
- `notification_preferences`: User preferences
- `preference_channels`: Junction table for preferences

## Running
```bash
# Build
mvn clean install

# Run
mvn spring-boot:run

# Or with custom properties
java -jar target/notification-1.0.0.jar \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/notification_db \
  --spring.artemis.broker-url=tcp://localhost:61616
