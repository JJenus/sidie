

# TESTING GUIDELINES

## Objective

Enforce deterministic, readable, maintainable tests with 90–100% coverage.
Tests define behavior. Production code must be refactorable without breaking tests that represent correct behavior.

No ad-hoc styles. No inconsistent naming. No fragile tests.

---

## Stack (mandatory)

* JUnit 
* Mockito
* Spring Boot Test
* Testcontainers (DB/Redis/JMS when needed)
* AssertJ
* Jacoco (coverage)

No alternative frameworks without justification.

---

## Coverage Rules

Minimum:

* Domain: 100%
* Application services: 95–100%
* Controllers: 90–95%
* Infrastructure: critical paths only
* Config/DTO/POJO getters/setters: ignore

Measure line + branch coverage.

Never write fake tests to inflate coverage.

---

## Test Types

### Unit

Scope:

* single class
* no Spring context
* all dependencies mocked

Targets:

* domain
* services
* validators
* factories
* parsers
* pure utilities

Fast. Milliseconds.

### Integration

Scope:

* multiple components
* real Spring context

Targets:

* controllers
* repositories
* cache
* messaging
* serialization
* config wiring

Use:

* @SpringBootTest or slice tests
* Testcontainers for DB/Redis/JMS

Slower but realistic.

### E2E (optional)

Only for:

* critical flows
* protocol pipelines
* messaging chains

---

## Directory Structure

Mirror production structure exactly.

Example:

```
src/main/java/com/jjenus/tracker/alerting/application/service/AlertRuleService.java
src/test/java/com/jjenus/tracker/alerting/application/service/AlertRuleServiceTest.java
```

Never place tests in random packages.

1:1 mapping between class and test class.

---

## Naming Conventions

### Test Class

```
<ClassName>Test
```

Examples:

```
AlertRuleServiceTest
GeofenceRuleValidatorTest
GT06ProtocolParserTest
```

Integration:

```
<ClassName>IT
<ClassName>IntegrationTest
```

Example:

```
AlertRuleControllerIT
GeofenceRepositoryIT
```

---

### Test Method

Format:

```
methodUnderTest_condition_expectedResult
```

Rules:

* lowercase
* underscores only
* no sentences
* no "test"

Examples:

```
createRule_validRequest_returnsSavedRule
createRule_invalidSpeed_throwsException
evaluate_idleTimeout_triggersAlert
parse_invalidChecksum_throwsProtocolException
findById_notFound_returnsEmpty
```

---

## Test Structure

Mandatory pattern:

### Arrange–Act–Assert

```
given
when
then
```

Example:

```java
@Test
void evaluate_speedExceeded_returnsAlert() {
    // given
    var rule = new MaxSpeedRule(80);
    var location = new TrackerLocation(...);

    // when
    boolean result = rule.evaluate(location);

    // then
    assertThat(result).isTrue();
}
```

No mixed setup/asserts.

---

## Unit Test Rules

### Isolation

* Mock all collaborators
* No DB
* No Spring
* No network
* No filesystem

Use:

```
@ExtendWith(MockitoExtension.class)
```

### Mocking

Mock only external dependencies.

Never mock:

* value objects
* domain entities
* simple DTOs

### Verification

Verify behavior only when interaction matters.

Bad:

```
verify(repository).save(...)
```

Good:

* when side effects matter (publishing event, saving record)

---

## Integration Test Rules

### Slices first

Prefer:

```
@WebMvcTest
@DataJpaTest
@JdbcTest
```

Use full:

```
@SpringBootTest
```

only when cross-layer wiring is required.

### Database

Use Testcontainers.

Never:

* shared DB
* local developer DB
* H2 when behavior differs from production

### HTTP

Use:

```
MockMvc or WebTestClient
```

Test:

* status
* JSON body
* validation errors

Not internal service calls.

---

## Assertions

Use AssertJ only.

Good:

```
assertThat(result).isEqualTo(expected);
assertThat(list).hasSize(2);
assertThatThrownBy(...).isInstanceOf(...)
```

Avoid:

```
assertTrue
assertEquals
System.out
```

---

## Test Data

### Builders required

Create test builders for complex objects.

Pattern:

```
AlertRuleTestBuilder
GeofenceTestBuilder
VehicleTestBuilder
```

Example:

```java
AlertRule rule = AlertRuleTestBuilder.defaultRule()
    .withSpeed(100)
    .build();
```

Never inline large constructors.

---

## Determinism

Tests must be:

* repeatable
* time independent
* order independent

Never:

* Thread.sleep
* random values
* current time

Use:

```
Clock.fixed()
UUID.fromString(...)
```

---

## Exceptions

Every business exception must have:

* success test
* failure test

Example:

```
throwsWhenInvalidSpeed
throwsWhenGeofenceEmpty
```

---

## Parameterized Tests

Use for rule engines, parsers, validators.

Example:

```
@ParameterizedTest
@CsvSource
```

Avoid duplicated tests.

---

## Domain Testing Strategy

For:

```
entity
value object
rule
factory
```

Test:

* invariants
* edge cases
* equals/hashcode
* boundary values
* illegal states

No mocks.

Pure logic only.

---

## Service Testing Strategy

Test:

* branching logic
* orchestration
* repository calls
* events
* exceptions

Mock:

* repositories
* publishers
* external services

---

## Controller Testing Strategy

Test:

* request validation
* status codes
* JSON contract
* error mapping

Never test service logic here.

---

## Repository Testing Strategy

Use:

```
@DataJpaTest + Testcontainers
```

Test:

* queries
* indexes
* mappings
* constraints

Never mock repositories.

---

## Messaging / Event Testing

Test:

* event published
* handler invoked
* payload correctness

Use in-memory or containerized broker.

Never hit real broker.

---

## Anti-Patterns (forbidden)

* testing private methods
* reflection hacks
* shared mutable state
* gigantic test classes
* more than one behavior per test
* logic inside tests
* copy-paste tests
* magic numbers
* println debugging
* silent catch blocks

---

## Size Limits

* 1 test file ≈ 300 lines max
* 1 test method tests 1 behavior only
* setup < assertions

If bigger, split.

---

## Required Test for Every New Class

When adding a class:

* create test immediately
* red → green → refactor
* no merge without tests

---

## CI Enforcement

Build fails if:

* coverage < threshold
* flaky tests
* skipped tests
* commented tests

---

## Definition of Done

Feature complete only when:

* happy path tested
* edge cases tested
* failures tested
* coverage threshold met
* deterministic
* readable

No exceptions.

---
