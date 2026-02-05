# Testing Strategy for Tracking Engine

This document describes **what is tested where** in the current repository structure.
How tests must be written (style, naming, patterns, rules) is defined in:

**[TESTING_GUIDELINES.md](TESTING_GUIDELINES.md) (mandatory reference).**

This file = scope map.
TESTING_GUIDELINES.md = rules.

Both must be followed.

---

# Module → Test Mapping

Every module mirrors:

```
src/main/java
src/test/java
```

Tests live in the exact same package structure as production code.

1:1 class ↔ test mapping.

---

## 1. shared

Path:

```
shared/src/test/java/com/jjenus/tracker/shared
```

### Scope

Pure domain + infrastructure utilities.

### What to test

### domain

* value objects
* invariants
* equals/hashCode
* edge cases

Examples:

* LocationPoint
* ConnectionInfo
* ConnectionMetadata

### exception

* hierarchy correctness
* message propagation
* wrapping behavior

Examples:

* DomainExceptionTest

### pubsub

* event publish/subscribe
* ordering
* handler invocation
* failure isolation

Examples:

* SimpleEventBusTest

### redis

* key generation logic
* serialization only (no real Redis for unit tests)

---

## 2. core-tracking

Path:

```
core-tracking/src/test/java/com/jjenus/tracker/core
```

### domain/entity

Test:

* Vehicle
* Trip
* Tracker
* TripPoint
* aggregates and invariants

Focus:

* state transitions
* calculations
* boundary conditions
* illegal states

Examples:

* VehicleTest
* TripTest
* LocationPointTest

No mocks.

---

### application/service

Test:

* DeviceCommandService
* VehicleService
* VehicleCommandService
* VehicleQueryService

Focus:

* orchestration
* repository interaction
* events
* branching logic

Mock:

* repositories
* publishers
* external systems

Examples:

* VehicleCommandServiceTest
* VehicleQueryServiceTest

---

### repository (integration)

Use:

* @DataJpaTest
* Testcontainers

Test:

* queries
* mappings
* constraints

Never mock repositories.

---

## 3. device-comm

Path:

```
device-comm/src/test/java/com/jjenus/tracker/devicecomm
```

### domain

* packet structures
* command objects

### infrastructure (parsers)

Critical.

Test:

* valid packets
* corrupted packets
* partial packets
* checksum failures
* protocol edge cases

These are deterministic unit tests.

Examples:

* AutoseekerProtocolParserTest
* GT06ProtocolParserTest

---

### application

* factories
* routing logic

Examples:

* ParserFactoryTest

---

### service

If networking involved:

* isolate parsing logic
* avoid real sockets for unit tests
* use integration tests only for full pipeline

---

## 4. alerting

Path:

```
alerting/src/test/java/com/jjenus/tracker/alerting
```

### domain (rules)

Highest priority.

Test:

* rule correctness
* boundaries
* no side effects

Classes:

* MaxSpeedRule
* IdleTimeRule
* GeofenceRule
* GeofenceExitRule
* GenericAlertRule
* AlertRuleFactory

No mocks.

---

### application/service

Test:

* AlertRuleService
* AlertRuleEvaluationService
* GeofenceService
* GeofenceRuleValidator
* AlertingEngine

Focus:

* orchestration
* rule evaluation order
* caching interaction
* repository calls
* exception paths

Mock:

* repositories
* cache services
* publishers

Examples:

* AlertRuleEvaluationServiceTest
* AlertingEngineTest

---

### api (controllers)

Use:

* @WebMvcTest
* MockMvc

Test:

* request validation
* status codes
* JSON contract
* error mapping

Do not test service logic.

Examples:

* AlertRuleControllerIT
* GeofenceControllerIT

---

### infrastructure

#### cache

* key generation
* cache hits/misses
* serialization

#### repository

* integration tests with Testcontainers

---

## 5. notification

Path:

```
notification/src/test/java/com/jjenus/tracker/notification
```

### domain/entity

* Notification
* Template
* Preferences
* state transitions

### application

* orchestrator
* dispatcher
* command/query services

Mock:

* delivery services
* repositories

### service (email/sms/push/websocket)

* formatting
* payload correctness
* retry logic
* failure handling

Never hit real providers.

### api

* controller contract tests with @WebMvcTest

### websocket

* handler behavior using mocked sessions

---

## 6. main-app

Path:

```
main-app/src/test/java
```

Scope:

* configuration
* bean wiring
* startup

Use:

* @SpringBootTest

Test:

* context loads
* JMS wiring
* configuration correctness

No business logic here.

---

# Test Categories Required Per Module

Each module must contain:

* domain unit tests
* service unit tests
* repository integration tests (if persistence exists)
* controller slice tests (if REST exists)

Missing category = incomplete module.

---

# Coverage Targets

Per module:

* domain: 100%
* services: 95–100%
* controllers: 90%+
* repositories: critical paths

Build fails below threshold.

---

# Running Tests

From root:

```
mvn clean test
```

Module only:

```
cd <module>
mvn test
```

Coverage:

```
mvn test jacoco:report
```

Report:

```
target/site/jacoco/index.html
```

---

# Mandatory Rule

Before adding or modifying code:

* create/update tests
* follow TESTING_GUIDELINES.md naming and structure rules
* no merge without coverage

TESTING_GUIDELINES.md is authoritative for:

* naming
* method structure
* mocking rules
* determinism
* builders
* anti-patterns
