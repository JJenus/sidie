# Session Plan — Tracking Engine Refactor

**Date:** 2026-08-30
**Status:** ✅ Phase 0 (Java 21) + Phase 1 (DDD Refactor) + Phase 2 (Architecture Fixes) — ALL COMPLETE
**Build:** `./mvnw -B clean install` passes all 7 modules. **602 tests, 0 failures.**

## Phase 0 — Java 21 Upgrade ✅
- `pom.xml`: `maven.compiler.source/target/release` → 21, `java.version` → 21
- `./mvnw` wrapper: Maven 3.9.9 + Java 21 pinned
- Surefire: `<forkCount>0</forkCount>` + `--add-opens` for module system

## Phase 1 — DDD Structural Refactor ✅

### 1.1–1.4: Unified Vehicle/Trip (POJO → JPA Entity) ✅
- Deleted pure POJOs: `core-tracking/.../domain/Vehicle.java`, `domain/Trip.java`
- JPA `entity/Vehicle.java`: `processNewTelemetry`, `issueFuelCutOffCommand`, `issueFuelRestoreCommand`, `getIdleDuration`, `getCurrentLocationPoint`, `startTrip`
- JPA `entity/Trip.java`: `calculateDistance()` and `calculateStatistics()` made public
- `VehicleCommandService` + `VehicleQueryService`: use `VehicleRepository` (JPA) + `Clock` bean

### 1.5: Moved FuelCutCommandHandler to core-tracking ✅
- `shared/.../pubsub/DeviceCommandTransport` SPI
- `shared/.../pubsub/DeviceCommandBuilder` SPI
- `core-tracking/.../application/FuelCutCommandHandler`
- `device-comm/.../service/ReactiveTcpCommandTransport`
- `GT06CommandBuilderAdapter` + `AutoseekerCommandBuilder` in device-comm

### 1.6: Moved AlertRuleFactory to application layer ✅
- `alerting/application/factory/AlertRuleFactory`
- `FactoryConfig` bean registration

### 1.10: IAlertRule Immutability ✅
- All rule implementations already have `final boolean enabled` + `isEnabled()`
- Interface has no `setEnabled()` — immutable by design

## Phase 2 — Architectural Fixes ✅

### 2.1: ConnectionInfo — Clock Injection ✅
- Already uses `Clock clock` parameter, no `reactor.netty.Connection` in domain

### 2.2: DomainEvent + Subclasses — Clock/UUID Injection ✅
- `DomainEvent`: static `setClock()`/`setUuidSupplier()` for test control; `protected` no-arg for JSON deserialization
- `LocationDataEvent`: `@JsonCreator` no-arg; primary constructor requires `Clock` + `UUID`
- `VehicleUpdatedEvent`: primary requires `Clock` + `UUID`
- `AlertRaisedEvent`: primary requires `Clock`
- `NotificationSentEvent`: primary requires `Clock`
- `FuelCutRequestedEvent`: requires `Clock`
- All callers updated: `DeviceDataProcessor`, `VehicleEventHandler`, `VehicleCommandService`, `AlertCreationEventHandler`, `RedisConnectionTracker`

### 2.3: GeofenceRule — wasInside Persistence ✅
- `RuleStateStore.getGeofenceWasInside()` / `setGeofenceWasInside()` via Redis
- `AlertRuleFactory`: loads previous state, calls `rule.setWasInside()`, saves after evaluate
- `RedisRuleStateStore`: `wasInside` stored in `rule:state:geofence:{ruleKey}:{vehicleId}` hash

### 2.4: IdleTimeRule — lastMovementTimes Persistence ✅
- `RuleStateStore.getLastMovementTime()` / `setLastMovementTime()` via Redis
- `AlertRuleFactory`: passes `persistedTimes` map + `persistTime` callback to rule
- `RedisRuleStateStore`: uses `rule:state:idle:{ruleKey}:{vehicleId}` hash

### 2.5: AlertDeduplicator + Cooldown ✅
- `AlertDeduplicator`: Redis `SET EX` with cooldown per rule+vehicle
- Configurable via `alerting.deduplication.cooldown-minutes`

## Feature Work ✅

### Device Disconnect Detection ✅
- `VehicleActivityTracker` (shared/redis): records `vehicleId → lastSeen` in Redis
- `DisconnectionScheduler`: `@Scheduled` scans every 60s, configurable threshold
- Uses `AlertDeduplicator` to avoid spam, raises `DEVICE_DISCONNECTED` events

### Prometheus Metrics ✅
- `MetricsRegistry` with Micrometer
- `actuator/prometheus` endpoint exposed

### Notification Retry + DLQ ✅
- `NotificationQueueConsumer`: Artemis queue consumer
- `DeliveryRetryScheduler`: exponential backoff with `NotificationBackoff`
- DLQ: `notification.retry.dlq-destination` configured
- `WebhookController`: webhook receiver with signature validation
- `DeliveryEvent` persisted for audit trail

### CSV Export ✅
- `TripExportController` — `GET /api/v1/trips/export`
- `TripExportService`: exports trips to CSV with proper streaming

### Geofence Dwell-Time ✅
- `RuleStateStore.getGeofenceEntryTime()` / `setGeofenceEntryTime()` / `clearGeofenceEntryTime()`
- `GeofenceRule`: new constructors with `maxDwellMinutes`, `entryTimeLoader`, `entryTimePersister`, `entryTimeClearer` callbacks
- `AlertType.GEOFENCE_DWELL_EXCEEDED` alert when dwell exceeded
- `AlertRuleFactory`: passes `maxDwellMinutes` param

### Auth Module (user-auth) ✅
- 8 JPA entities: User, Identity, Organization, Permission, Role, Session, RefreshToken, LoginAttempt
- 8 Spring Data JPA repositories
- Security: `JwtService` (HS256), `PasswordService` (BCrypt 12), `TokenHashService` (SHA-256)
- Token rotation with reuse detection + full chain revocation
- Account lockout: 5 failed attempts in 30 min
- `TenantContext` (ThreadLocal `orgId`) from JWT
- Controllers: `AuthController`, `UserController`, `OrganizationController`
- Services: `AuthService`, `OrganizationService`, `LoginAttemptService`, `PermissionService`
- 2 Flyway migrations: V1 (schema) + V2 (permissions + default roles seed)
- 97 tests: domain entities, security, services
- `organizationId` added to `Vehicle` and `AlertRule` for multi-tenancy

## Test Results
```
shared:              OK
core-tracking:       OK
device-comm:         OK
alerting:           74 ✅
notification:        43 ✅
user-auth:           97 ✅
main-app:            OK (no tests)
TOTAL:             602 tests, 0 failures ✅
```

## Remaining (Out of Scope)
- Protocol parsers (GT06, Autoseeker): `Instant.now()` for packet timestamps — device-level, acceptable
- Entity `createdAt`/`updatedAt` defaults: JPA `@PrePersist`/`@PreUpdate` is the future fix
- notification module: `Instant.now()` in entities/services — deferred for next phase
- user-auth module: same, deferred

## Run Commands
```bash
./mvnw -B clean install
./mvnw -B test -DforkCount=0
./mvnw -pl alerting -B test -DforkCount=0
cd main-app && ../mvnw exec:java
```
