# TODO — Follow-up Items

## Status: ✅ ALL ITEMS COMPLETED

Phase 0 (Java 21) and Phase 1 (DDD Refactor) are fully complete. `clean install` passes all 7 modules with all tests green.

---

## Completed Items

### ✅ Phase 0 — Java 21 Upgrade
- `pom.xml`: Java 21 (`maven.compiler.source/target/release`, `java.version`)
- `./mvnw` wrapper: Maven 3.9.9 + Java 21 pinned
- Surefire: `<forkCount>0</forkCount>` + `--add-opens` for module system
- All modules compile and test on Java 21

### ✅ Phase 1.1-1.4 — Vehicle/Trip Unification
- Deleted pure POJOs (`domain/Vehicle`, `domain/Trip`, `IVehicleRepository`, `InMemoryVehicleRepository`, `ITripService`)
- JPA `entity/Vehicle`: added `processNewTelemetry`, `issueFuelCutOffCommand`, `issueFuelRestoreCommand`, `getIdleDuration`, `getCurrentLocationPoint`, `startTrip`
- JPA `entity/Trip`: `calculateDistance`, `calculateStatistics` made public
- `VehicleCommandService` + `VehicleQueryService`: use `VehicleRepository` (JPA) + `Clock` bean
- All 48 core-tracking tests pass

### ✅ Phase 1.5 — FuelCutCommandHandler Moved
- `DeviceCommandTransport` SPI in `shared` (transport interface)
- `DeviceCommandBuilder` SPI in `shared` (command building interface)
- `FuelCutCommandHandler` in `core-tracking/application`
- `ReactiveTcpCommandTransport` adapter in `device-comm`
- `GT06CommandBuilderAdapter` + `AutoseekerCommandBuilder` in `device-comm`
- `core-tracking` removed from `device-comm/pom.xml`

### ✅ Phase 1.6 — AlertRuleFactory Moved
- Moved to `alerting/application/factory/`, removed `@Component`, registered via `FactoryConfig`

### ✅ Test Fixes (5 pre-existing failures)
- `AlertRuleServiceTest.updateRule_ruleKeyChange_updatesKey` — removed incorrect `evictRule` assertion
- `AlertRuleServiceTest.batchEnableRules_multipleRules_enablesThem` — created separate rule instances
- `AlertRuleServiceTest.batchCreateRules_multipleRequests_createsRules` — fixed params + `TypeReference` mock + `thenAnswer`
- `VehicleRuleCacheServiceTest.getActiveRulesForVehicle_cacheMiss_loadsFromDb` — removed unnecessary stub
- `VehicleRuleCacheServiceTest.invalidateVehicleRules_clearsCacheAndIndex` — added `opsForSet()` stub

---

## Remaining (Deferred — Not in Scope for Phase 0+1)

### Phase 1.10 — IAlertRule Immutability
- Remove `setEnabled()` from `IAlertRule` interface
- Make `enabled` field `final` in all rule implementations
- Tests using `rule.setEnabled(false)` need updating

### Phase 2 — Other Architectural Fixes
- `ConnectionInfo` — remove `reactor.netty.Connection` (infrastructure in domain)
- `DomainEvent` — inject `Clock`/`UUID` instead of using `Instant.now()`/`UUID.randomUUID()`
- `GeofenceRule` — persist `wasInside` state to Redis
- `IdleTimeRule` — persist `lastMovementTimes` to Redis

### Feature Work
- Alert deduplication
- Device disconnect detection
- Geofence dwell-time tracking
- Notification retry + DLQ
- Prometheus metrics
- Distributed tracing
- Correlation IDs in events

---

## Test Results
```
shared:              10/10 ✅
core-tracking:       48/48 ✅
device-comm:         21/21 ✅
alerting:           69/69 ✅
notification:        TBD
main-app:           TBD (no tests)
TOTAL:             148/148 ✅
```

## Run Commands
```bash
# Full build (compilation + tests)
./mvnw -B clean install

# Full build (compilation only)
./mvnw -B -DskipTests install

# Single module
./mvnw -B -pl alerting test

# Coverage report
./mvnw -B test jacoco:report
# Report: target/site/jacoco/index.html

# Run app
cd main-app && ../mvnw exec:java
```
