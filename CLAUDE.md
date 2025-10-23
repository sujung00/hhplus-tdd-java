# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

**hhplus-tdd-jvm** is a TDD (Test-Driven Development) focused Java project implementing a point management system. The project follows the Red-Green-Refactor cycle with emphasis on testability and clean architecture.

### Key Stack
- **Language**: Java 17
- **Framework**: Spring Boot
- **Build Tool**: Gradle (Kotlin DSL)
- **Testing**: JUnit5, AssertJ
- **Code Quality**: JaCoCo (code coverage)

---

## Quick Commands

### Build & Test
```bash
# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "PointServiceTest"

# Run specific test method
./gradlew test --tests "PointServiceTest.ChargeTests.shouldCreateNewUserWhenCharging"

# Run tests with coverage report
./gradlew test jacocoTestReport

# Clean build
./gradlew clean build

# Check for test failures without stopping
./gradlew test (ignoreFailures = true is configured)
```

### Development
```bash
# Start application
./gradlew bootRun

# Check Gradle tasks
./gradlew tasks

# View dependency tree
./gradlew dependencies
```

---

## Architecture & Code Structure

### Package Organization

```
io.hhplus.tdd
├── point/                    # Point management domain
│   ├── PointService         # Business logic (TDD implementation)
│   ├── PointController      # REST endpoints
│   ├── UserPoint            # Data model (record)
│   ├── PointHistory         # Transaction history model (record)
│   └── TransactionType      # Enum (CHARGE, USE)
│
├── database/                 # Data access layer (persistence)
│   ├── UserPointTable       # User point storage (no modifications allowed)
│   └── PointHistoryTable    # Transaction history storage (no modifications)
│
├── TddApplication           # Spring Boot entry point
├── ApiControllerAdvice      # Global exception handler
└── ErrorResponse            # Error response DTO
```

### Core Architecture Pattern

**Domain-Driven with Separation of Concerns:**

1. **PointService** (Business Logic Layer)
   - Core domain logic for point operations
   - Implements charge, use, getPoint, getHistories methods
   - Validates business rules (amount > 0, sufficient balance, etc.)
   - Coordinates between UserPointTable and PointHistoryTable

2. **Database Layer** (Immutable)
   - `UserPointTable`: Manages current user point balance
     - `selectById(userId)`: Returns UserPoint (0 if user doesn't exist)
     - `insertOrUpdate(userId, amount)`: Updates or creates user point
   - `PointHistoryTable`: Maintains audit trail of all transactions
     - `insert(userId, amount, type, updateMillis)`: Records transaction
     - `selectAllByUserId(userId)`: Returns all user transactions (filtered by userId)
   - **Important**: Both table classes use throttle() to simulate I/O latency

3. **PointController** (API Layer)
   - REST endpoints for point operations
   - Delegates business logic to PointService

### Data Flow

```
User Request → PointController → PointService → Database Layer
                                      ↓
                         UserPointTable (for balance)
                         PointHistoryTable (for audit)
                                      ↓
                              Response Back
```

---

## TDD Development Cycle (Red-Green-Refactor)

### Phase 1: RED - Write Failing Tests
When starting a new feature:

```bash
# 1. Create test file with @DisplayName annotations
# 2. Write test cases for all requirements
# 3. Run tests - they MUST fail initially
./gradlew test --tests "YourNewTest"
# Expected: BUILD SUCCESSFUL with test failures
```

### Phase 2: GREEN - Implement to Pass Tests
```bash
# 1. Implement minimal code to pass tests
# 2. Run tests - they MUST pass
./gradlew test --tests "YourNewTest"
# Expected: BUILD SUCCESSFUL with all tests passing
```

### Phase 3: REFACTOR - Improve Code
- Extract common validation logic to private methods
- Improve code clarity and maintainability
- Ensure tests still pass

---

## Key Implementation Details

### PointService Methods

**charge(long userId, long amount)**
- Validates amount > 0 (throws IllegalArgumentException)
- Retrieves current user point (0 if non-existent = auto-creates)
- Adds amount to existing balance
- Updates UserPointTable with new balance
- Records transaction in PointHistoryTable with CHARGE type
- Returns updated UserPoint

**use(long userId, long amount)**
- Validates amount > 0 (throws IllegalArgumentException)
- Retrieves current user point
- Validates balance >= amount (throws IllegalArgumentException if insufficient)
- Subtracts amount from balance
- Updates UserPointTable with new balance
- Records transaction in PointHistoryTable with USE type
- Returns updated UserPoint

**getPoint(long userId)**
- Returns current balance for user (0 if non-existent)
- No side effects, pure query operation

**getHistories(long userId)**
- Returns all transactions for user (already sorted by time in PointHistoryTable)
- Empty list if user has no history
- Maintains chronological order

### Important Constraints
- **Never modify** UserPointTable and PointHistoryTable implementations
- Only use their public APIs: selectById, insertOrUpdate, insert, selectAllByUserId
- All business logic validation should be in PointService
- Use `System.currentTimeMillis()` for transaction timestamps
- Data access always returns consistent state (UserPoint uses record type)

---

## Testing Guidelines

### Test Structure
Tests follow `@Nested` with `@DisplayName` for organization:

```java
@DisplayName("PointService 단위 테스트")
class PointServiceTest {
    @Nested
    @DisplayName("charge - 포인트 충전")
    class ChargeTests {
        @Test
        @DisplayName("존재하지 않는 사용자는 새로 생성되어야 한다")
        void shouldCreateNewUserWhenCharging() { ... }
    }
}
```

### Test Patterns

**Given-When-Then Pattern:**
```java
@Test
@DisplayName("test description")
void testMethodName() {
    // given - setup preconditions
    long userId = 1L;
    long amount = 1000L;

    // when - execute action
    UserPoint result = pointService.charge(userId, amount);

    // then - verify results
    assertThat(result)
        .isNotNull()
        .extracting(UserPoint::id, UserPoint::point)
        .containsExactly(userId, amount);
}
```

### AssertJ Best Practices
- Use fluent API: `assertThat().isNotNull().extracting()...`
- Prefer `containsExactly()` over multiple assertions
- Use `filteredOn()` and `hasSize()` for collection testing
- Test both happy path and exception cases

---

## Important Notes for TDD Development

1. **Test First Mindset**: Write tests before implementation
   - Forces thinking about API contracts
   - Ensures testability by design

2. **Exception Testing**: Always test error conditions
   - Use `assertThatThrownBy(() -> method()).isInstanceOf(ExceptionType.class)`

3. **Data Consistency**: PointService must ensure atomicity
   - Update UserPointTable AND PointHistoryTable in same transaction
   - System.currentTimeMillis() called at operation time, not after

4. **Edge Cases to Test**:
   - Zero and negative amounts
   - Non-existent users (should auto-initialize to 0)
   - Exact balance usage (use all points)
   - Multiple consecutive operations
   - Transaction history ordering

5. **Database Layer Throttling**:
   - UserPointTable.selectById() has 200ms throttle
   - PointHistoryTable.insert() has 300ms throttle
   - Tests will be slower due to this I/O simulation - this is expected

---

## Spring Boot Application

**TddApplication.java**: Minimal Spring Boot app with RestController for HTTP endpoints

```java
@RestController
@RequestMapping("/point")
public class PointController {
    // GET /point/{id} - getPoint
    // GET /point/{id}/histories - getHistories
    // PATCH /point/{id}/charge - charge
    // PATCH /point/{id}/use - use
}
```

Error handling is managed by `ApiControllerAdvice` with `ErrorResponse` DTO.

---

## PR Template Requirements

When creating pull requests, reference the checklist in `.github/pull_request_template.md`:

### TDD Basics
- Point management features implemented
- Unit tests written for each feature
- Red-Green-Refactor cycle followed
- Code structured for testability

### Key Requirements
- All tests must pass before merging
- Exception cases must be tested
- Transaction history must be auditable
- Code follows Spring Boot conventions

---

## Useful Resources

### Key Files to Read
- `build.gradle.kts`: Build configuration (Gradle wrapper, dependencies, JaCoCo setup)
- `.github/pull_request_template.md`: PR template with TDD checklist
- `gradle.properties`: Gradle settings (Java 17, parallel builds enabled)

### Common Development Patterns
- **Validation**: Use `validateAmount()` pattern for input validation
- **Query**: PointService query methods (getPoint, getHistories) have no side effects
- **Mutation**: PointService mutation methods (charge, use) update both tables atomically
- **Exception Strategy**: IllegalArgumentException for validation failures

---

## Performance Considerations

- **Throttling**: Database tables simulate I/O with random sleep
  - selectById: 0-200ms
  - insertOrUpdate: 0-300ms
  - insert: 0-300ms
- **Gradle Settings**: Parallel builds and build caching enabled (gradle.properties)
- **Test Execution**: ignoreFailures=true allows test execution to continue even with failures

---

## Code Quality

### JaCoCo Integration
```bash
./gradlew jacocoTestReport
# Generates coverage report in: build/reports/jacoco/test/html/index.html
```

### Configuration
- Java 17 source compatibility
- Spring Boot 3.x with Spring Dependency Management
- Lombok for boilerplate reduction
- JUnit5 with AssertJ assertions
