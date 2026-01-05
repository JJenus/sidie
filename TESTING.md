# Testing Strategy for Tracking Engine

## Test Structure

The project follows a comprehensive testing strategy with unit tests for each module:

### 1. **Shared Module Tests**
- `DomainExceptionTest.java` - Tests for custom exception hierarchy
- `SimpleEventBusTest.java` - Tests for pub/sub event system

### 2. **Core Tracking Module Tests**
- `LocationPointTest.java` - Tests for value object validation and calculations
- `VehicleTest.java` - Comprehensive tests for Vehicle aggregate root
- `TripTest.java` - Tests for Trip entity and calculations
- `VehicleCommandServiceTest.java` - Mock-based tests for application service
- `VehicleQueryServiceTest.java` - Tests for query service with repository mocking

### 3. **Device Communication Module Tests**
- `Gt06ProtocolParserTest.java` - Tests for GT06 protocol parsing
- `AutoseekerProtocolParserTest.java` - Tests for Autoseeker protocol parsing
- `ParserFactoryTest.java` - Tests for protocol parser factory

### 4. **Alerting Module Tests**
- `MaxSpeedRuleTest.java` - Tests for speed-based alert rule
- `IdleTimeRuleTest.java` - Tests for idle time alert rule
- `AlertingEngineTest.java` - Comprehensive tests for alert engine with mocking
- `AlertRuleEvaluationServiceTest.java` - Tests for rule evaluation service

## Test Coverage

### Domain Model Testing
- **Value Objects**: Immutability, validation, calculations
- **Entities**: Business rules, state transitions, encapsulation
- **Aggregates**: Invariants, consistency boundaries

### Application Services Testing
- **Command Services**: Business logic, validation, event publishing
- **Query Services**: Data retrieval, repository interaction
- **Mock Dependencies**: Repository, event publisher mocking

### Infrastructure Testing
- **Protocol Parsers**: Data parsing, error handling, edge cases
- **Factories**: Object creation, dependency resolution

### Alert System Testing
- **Rules**: Condition evaluation, alert generation
- **Engine**: Rule registration, evaluation order, error handling

## Running Tests

### Using Maven
```bash
# Run all tests
mvn test

# Run tests for specific module
cd core-tracking
mvn test

# Run with coverage report
mvn test jacoco:report
