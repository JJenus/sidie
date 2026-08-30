# AGENTS.md — Tracking Engine

## Project Overview

Java 21 + Maven multi-module Spring Boot 3.5.9 DDD tracking engine.
Uses the `./mvnw` wrapper (pins Maven 3.9.9 + Java 21 automatically).

## Modules & Dependency Graph

```
shared (foundation: events, exceptions, SPI interfaces, Redis tracker)
  ↑
core-tracking (depends on shared)
  ↑
device-comm  alerting (both depend on shared; device-comm is pure transport, no business logic)
  ↑
  notification (depends on shared; consumer of Alerting events)
  ↑
  main-app (depends on ALL; composition root, Spring Boot entrypoint)
```

**Rule**: Build `shared` first. `./mvnw -B -DskipTests install` from root before running module-specific tests.

## Build & Run

```bash
# Use the project wrapper (pins Java 21 + Maven 3.9.9)
./mvnw clean install

# Run all tests (in-process, no fork)
./mvnw -B test -DforkCount=0

# Single module tests
./mvnw -pl core-tracking -B test -DforkCount=0

# Coverage report
./mvnw test jacoco:report
# Report: target/site/jacoco/index.html

# Run application
cd main-app && ../mvnw exec:java
# Main class: com.jjenus.tracker.main.TrackingEngineApplication
```

## Testing

**Authoritative test rules**: `TESTING_GUIDELINES.md` (naming, structure, mocking, anti-patterns). `TESTING.md` is the scope map.

### Key conventions

- **Test class naming**: `<ClassName>Test` (unit), `<ClassName>IT` or `<ClassName>IntegrationTest` (integration)
- **Test method naming**: `methodUnderTest_condition_expectedResult` (lowercase, underscores, no "test")
- **Structure**: `given` / `when` / `then` blocks
- **Assertions**: AssertJ only
- **Test data**: Builders required (`*TestBuilder`) — never inline large constructors
- **Determinism**: `Clock.fixed()`, `UUID.fromString(...)` — no `Thread.sleep`, no random, no current time

### Mocking rules

- **Domain tests (entities, VOs, rules, factories)**: NO mocks. Pure logic.
- **Service tests**: Mock repositories, publishers, external services
- **Never mock**: value objects, domain entities, simple DTOs
- **Integration tests**: Use `@DataJpaTest + Testcontainers` for DB; `@WebMvcTest` for controllers; `@SpringBootTest` only for cross-layer wiring

### Coverage thresholds (build fails if not met)

- Domain: 100%
- Services: 95–100%
- Controllers: 90%+
- Repositories: critical paths
- Config/DTO/POJO getters-setters: ignored

### Test stack

JUnit 5, Mockito, Spring Boot Test, Testcontainers (DB/Redis/JMS), AssertJ, Jacoco.

## Architecture Notes

- **No Spring Boot parent POM** — this is a custom parent pom with imported `spring-boot-dependencies` BOM
- **No Lombok** — use explicit getters/setters/constructors
- Event bus pattern: `LocationDataEvent` (Device-Comm → Core-Tracking), `VehicleUpdatedEvent` (Core-Tracking → Alerting), `AlertRaisedEvent` (Alerting → Notification)
- `main-app` uses `scanBasePackages = "com.jjenus.tracker.**"` to auto-discover all beans
- Alerting module uses H2 database (primary for now); notification module uses H2
- `shared` module contains events, exception hierarchy, pubsub interfaces (including `DeviceCommandTransport`, `DeviceCommandBuilder` SPI), Redis tracker
- **device-comm is pure transport** — no business logic. Commands go through `FuelCutCommandHandler` in core-tracking, which delegates to `DeviceCommandTransport` (implemented by `ReactiveTcpCommandTransport` in device-comm)
- **Vehicle and Trip are JPA entities** — business methods are on the entity, not on pure POJOs (pure POJOs deleted)
- **AlertRuleFactory** lives in `alerting/application/factory/` (application layer, not domain), registered via `FactoryConfig`

## Important Files

| File | Purpose |
|------|---------|
| `pom.xml` | Root parent; Java 21, dependency management |
| `mvnw` | Project wrapper — pins Java 21 + Maven 3.9.9 |
| `TESTING_GUIDELINES.md` | Authoritative test rules (naming, mocking, structure) |
| `TESTING.md` | Module → test mapping / scope |
| `README.md` | Architecture overview |
| `main-app/src/main/resources/application.yaml` | Full app config (H2, Redis, Artemis, JMS) |

## Gotchas

- Use `./mvnw` (not system `mvn`) — pins Java 21 automatically
- `./mvnw -B test -DforkCount=0` runs tests in-process (required for Java 21 compatibility)
- `shared` must be built first due to dependency chain
- Alerting's `spring-boot-starter` deps are `provided` scope (main-app provides runtime)
- No CI workflows exist in this repo — build verification is manual
- Python protocol simulators live in `tools/python-simulators/` (gt06-device, autoseeker-device) — no Python deps needed
