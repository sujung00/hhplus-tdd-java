# hhplus-tdd-jvm: Spring Boot TDD 포인트 관리 시스템

## 프로젝트 개요

**hhplus-tdd-jvm**은 TDD(Test-Driven Development) 방법론을 기반으로 개발된 포인트 관리 시스템입니다. 사용자의 포인트 충전, 사용, 조회 및 거래 내역 관리 기능을 제공하며, 동시성 제어를 통해 안전한 멀티스레드 환경을 지원합니다.

### 주요 특징
- ✅ TDD 기반 개발 (Red-Green-Refactor)
- ✅ 포인트 정책 검증 (최대 잔고, 최소/최대 금액)
- ✅ 동시성 제어 (ReentrantLock)
- ✅ 포괄적인 테스트 (단위 테스트 + 통합 테스트 + 동시성 테스트)
- ✅ Spring Boot 기반 REST API

---

## 기술 스택

| 항목 | 버전 |
|------|------|
| **Java** | 17 |
| **Spring Boot** | 3.2.0 |
| **Gradle** | 8.4 (Kotlin DSL) |
| **JUnit** | 5 |
| **AssertJ** | Latest |
| **Lombok** | Latest |

---

## 빠른 시작

### 빌드
```bash
./gradlew clean build
```

### 테스트 실행
```bash
# 모든 테스트 실행
./gradlew test

# 단위 테스트만 실행
./gradlew test --tests "*ServiceTest"

# 통합 테스트만 실행
./gradlew test --tests "*IntegrationTest"

# 특정 테스트 클래스 실행
./gradlew test --tests "PointServiceTest"

# 커버리지 보고서 생성
./gradlew jacocoTestReport
```

### 애플리케이션 실행
```bash
./gradlew bootRun
```

---

## API 명세

### 포인트 조회
```http
GET /point/{userId}
```

**응답 예시:**
```json
{
  "id": 1,
  "point": 50000,
  "updateMillis": 1698000000000
}
```

### 포인트 충전
```http
PATCH /point/{userId}/charge
Content-Type: application/json

10000
```

**응답 예시:**
```json
{
  "id": 1,
  "point": 60000,
  "updateMillis": 1698000001000
}
```

### 포인트 사용
```http
PATCH /point/{userId}/use
Content-Type: application/json

5000
```

**응답 예시:**
```json
{
  "id": 1,
  "point": 55000,
  "updateMillis": 1698000002000
}
```

### 거래 내역 조회
```http
GET /point/{userId}/histories
```

**응답 예시:**
```json
[
  {
    "id": 1,
    "userId": 1,
    "amount": 10000,
    "type": "CHARGE",
    "updateMillis": 1698000001000
  },
  {
    "id": 2,
    "userId": 1,
    "amount": 5000,
    "type": "USE",
    "updateMillis": 1698000002000
  }
]
```

---

## 포인트 정책

### 정책 상수

| 정책 | 값 |
|------|-----|
| 최대 잔고 | 1,000,000원 |
| 최소 충전 금액 | 100원 |
| 최대 충전 금액 | 1,000,000원 |
| 최소 사용 금액 | 100원 |
| 최대 사용 금액 | 1,000,000원 |

### 검증 규칙

#### 충전 시
- 0 초과의 금액만 가능
- 최소 100원, 최대 1,000,000원
- 충전 후 포인트가 최대 1,000,000원을 초과하면 실패

#### 사용 시
- 0 초과의 금액만 가능
- 최소 100원, 최대 1,000,000원
- 잔고가 사용 금액보다 적으면 실패

---

## Java 동시성 제어

### 개요

이 프로젝트는 **ReentrantLock**을 사용하여 멀티스레드 환경에서의 데이터 일관성을 보장합니다.

### 선택 이유

#### ReentrantLock을 선택한 이유

**장점:**
- ✅ **명시적인 제어**: lock()과 unlock() 호출로 명확한 제어 가능
- ✅ **타임아웃 지원**: tryLock(timeout) 으로 데드락 방지 가능
- ✅ **사용자별 세분화**: ConcurrentHashMap을 통해 사용자별 독립적인 락 관리
- ✅ **공정성 보장**: ReentrantLock(true)으로 FIFO 순서 보장 가능
- ✅ **InterruptedException 지원**: 스레드 중단에 대한 제어 가능

**단점:**
- ⚠️ synchronized보다 코드가 복잡함
- ⚠️ finally 블록에서 unlock() 호출 필수

#### 다른 방식과의 비교

| 방식 | 장점 | 단점 | 사용 상황 |
|------|------|------|----------|
| **synchronized** | 간단하고 명확 | 타임아웃 없음, 세분화 어려움 | 간단한 임계 영역 |
| **ReentrantLock** | 세분화, 타임아웃 지원 | 복잡함 | 복잡한 동시성 요구사항 |
| **ConcurrentHashMap** | 자동 동시성 관리 | 전체 데이터만 보호 | 컬렉션 전체 보호 |
| **AtomicInteger** | Lock-free | 단순 연산만 가능 | 원자적 연산 |
| **StampedLock** | 고성능 | 매우 복잡함 | 극도로 높은 동시성 |

### 구현 방식

#### 아키텍처

```java
private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

// 사용자별 독립적인 락 관리
ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
```

#### 코드 예시

```java
public UserPoint charge(long userId, long amount) {
    // 1. 입력 검증
    validateChargeAmount(amount);

    // 2. 사용자별 락 획득
    ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());

    try {
        // 3. 타임아웃이 있는 락 획득 (데드락 방지)
        if (!lock.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("포인트 충전 중 타임아웃이 발생했습니다");
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("포인트 충전이 중단되었습니다", e);
    }

    try {
        // 4. 임계 영역 (락이 잠긴 상태에서 실행)
        UserPoint currentPoint = userPointTable.selectById(userId);
        long newAmount = currentPoint.point() + amount;
        validateMaxPoint(newAmount);

        UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newAmount);
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());

        return updatedPoint;
    } finally {
        // 5. 반드시 락 해제 (finally 블록 필수)
        lock.unlock();
    }
}
```

### 동시성 보장 분석

#### 시나리오: 동일 사용자에 대한 동시 충전

```
스레드1 (충전 5,000원)    스레드2 (충전 3,000원)
        |                        |
        v                        v
    lock.tryLock()          lock.tryLock()
        |                        |
        +---> 획득 성공           +---> 대기 (스레드1이 잠금)
        |                        |
        v                        |
   읽기: 0원                     |
   연산: 0 + 5,000 = 5,000      |
   쓰기: 5,000                  |
   히스토리 저장                  |
        |                        |
        v                        |
    lock.unlock()               |
        |                        v
        |                   획득 성공
        |                        |
        |                   읽기: 5,000원
        |                   연산: 5,000 + 3,000 = 8,000
        |                   쓰기: 8,000
        |                   히스토리 저장
        |                        |
        |                   lock.unlock()
```

**결과**: 최종 포인트 = 8,000원 (정확함) ✓

#### 시나리오: 동시 사용으로 인한 race condition 방지

```
스레드1 (사용 7,000원)     스레드2 (사용 5,000원)
        |                       |
        v                       v
    lock.tryLock()          lock.tryLock()
        |                       |
        +---> 획득 성공          +---> 대기
        |                       |
        v                       |
   읽기: 10,000원               |
   검증: 10,000 >= 7,000 ✓      |
   연산: 10,000 - 7,000 = 3,000 |
   쓰기: 3,000                  |
        |                       |
        v                       |
    lock.unlock()              |
        |                       v
        |                  획득 성공
        |                       |
        |                  읽기: 3,000원
        |                  검증: 3,000 >= 5,000 ✗
        |                  예외 발생 (부족)
```

**결과**: 스레드2 실패, 데이터 일관성 보장 ✓

### 성능 특성

#### 메모리 사용량
- 사용자당 1개의 ReentrantLock 객체 생성
- ConcurrentHashMap 오버헤드

#### 응답 시간
- 같은 사용자: 락 대기로 인한 지연 (순차 처리)
- 다른 사용자: 병렬 처리 (지연 없음)

#### 타임아웃 설정
```java
private static final long LOCK_TIMEOUT_MILLIS = 5000L;  // 5초

if (!lock.tryLock(LOCK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
    throw new IllegalStateException("타임아웃");
}
```

**이유**:
- 데드락 방지
- 무한 대기 회피
- 시스템 반응성 보장

---

## 테스트 전략

### 1. 단위 테스트 (PointServiceTest)
- 각 메서드의 기본 기능 검증
- 정책 위반 시 예외 발생 확인
- 계산 정확성 검증

### 2. 통합 테스트 (PointControllerIntegrationTest)
- Spring Boot 환경에서 REST API 검증
- 컨트롤러-서비스 통합 동작 확인

### 3. 동시성 테스트
- ExecutorService 활용한 멀티스레드 시뮬레이션
- CountDownLatch로 동시성 동기화
- 예상 결과값과의 정확성 비교

#### 동시성 테스트 코드 예시

```java
@Test
void shouldHandleConcurrentChargesCorrectly() throws InterruptedException {
    // given
    long chargeAmount = 1000L;
    int threadCount = 10;
    int operationsPerThread = 10;

    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    // when
    for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
            try {
                for (int j = 0; j < operationsPerThread; j++) {
                    pointController.charge(testUserId, chargeAmount);
                }
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();  // 모든 스레드 완료 대기
    executorService.shutdown();

    // then
    UserPoint result = pointController.point(testUserId);
    long expectedPoint = chargeAmount * threadCount * operationsPerThread;
    assertThat(result.point()).isEqualTo(expectedPoint);
}
```

### 테스트 실행 결과
- ✅ 단위 테스트: 20개 모두 통과
- ✅ 통합 테스트: 20개 모두 통과
- ✅ 동시성 테스트: 4개 모두 통과

---

## 프로젝트 구조

```
hhplus-tdd-jvm/
├── src/
│   ├── main/java/io/hhplus/tdd/
│   │   ├── point/
│   │   │   ├── PointService.java          # 비즈니스 로직
│   │   │   ├── PointController.java       # REST API
│   │   │   ├── UserPoint.java             # 데이터 모델
│   │   │   ├── PointHistory.java          # 거래 내역
│   │   │   └── TransactionType.java       # 거래 유형
│   │   ├── database/
│   │   │   ├── UserPointTable.java        # 데이터 접근
│   │   │   └── PointHistoryTable.java     # 거래 내역 접근
│   │   └── TddApplication.java            # Spring Boot 진입점
│   └── test/java/io/hhplus/tdd/point/
│       ├── PointServiceTest.java          # 단위 테스트
│       └── PointControllerIntegrationTest.java  # 통합 테스트
├── build.gradle.kts                       # Gradle 설정
└── README.md                              # 이 파일
```

---

## TDD 개발 사이클

### Red Phase (실패하는 테스트 작성)
```bash
# 1. 테스트 작성 (실패함을 확인)
./gradlew test --tests "PointServiceTest"
# FAILED: 20 tests, 20 failures
```

### Green Phase (최소 구현)
```bash
# 2. 기본 구현
# - charge(): 포인트 충전 로직
# - use(): 포인트 사용 로직
# - getPoint(): 조회
# - getHistories(): 내역 조회

./gradlew test --tests "PointServiceTest"
# PASSED: 20 tests, 0 failures
```

### Refactor Phase (개선)
```bash
# 3. 기능 추가
# - @Service 등록
# - ReentrantLock으로 동시성 제어
# - 포인트 정책 검증
# - 타임아웃 처리

./gradlew test
# PASSED: 44 tests (단위 + 통합 + 동시성)
```

---

## 개발 팁

### 1. 테스트 작성 시
```java
@Test
@DisplayName("유의미한 한글 설명")
void shouldBehaviorWhenCondition() {
    // given: 선행 조건 설정
    long userId = 1L;
    long amount = 1000L;

    // when: 액션 실행
    UserPoint result = pointService.charge(userId, amount);

    // then: 결과 검증
    assertThat(result.point()).isEqualTo(amount);
}
```

### 2. 동시성 테스트 시
```java
ExecutorService executorService = Executors.newFixedThreadPool(10);
CountDownLatch latch = new CountDownLatch(10);

// 각 스레드 작업 제출
for (int i = 0; i < 10; i++) {
    executorService.submit(() -> {
        try {
            // 작업
        } finally {
            latch.countDown();
        }
    });
}

latch.await();  // 모든 스레드 완료 대기
```

### 3. 정책 검증 추가 시
```java
private static final long MAX_POINT = 1_000_000L;

private void validateMaxPoint(long point) {
    if (point > MAX_POINT) {
        throw new IllegalArgumentException(
            "포인트는 최대 " + MAX_POINT + "원을 초과할 수 없습니다"
        );
    }
}
```

---

## 주요 학습 포인트

### 1. 동시성 제어 방식 선택

| 상황 | 추천 방식 | 이유 |
|------|----------|------|
| 간단한 임계 영역 | synchronized | 간결함 |
| 복잡한 임계 영역 | ReentrantLock | 타임아웃, 세분화 |
| 단순 연산 | Atomic* | Lock-free |
| 컬렉션 전체 | ConcurrentHashMap | 내장 동시성 |

### 2. 데드락 방지 방법

```java
// ❌ 나쁜 예 (데드락 위험)
lock1.lock();
lock2.lock();

// ✅ 좋은 예 (타임아웃으로 데드락 방지)
if (!lock.tryLock(5, TimeUnit.SECONDS)) {
    throw new TimeoutException();
}
```

### 3. finally 블록의 중요성

```java
try {
    lock.lock();  // 또는 lock.tryLock()
    // 임계 영역
} finally {
    lock.unlock();  // 반드시 해제
}
```

---

## 참고 자료

- [Java Concurrency in Practice](https://jcip.net/)
- [ReentrantLock JavaDoc](https://docs.oracle.com/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html)
- [Spring Boot 공식 문서](https://spring.io/projects/spring-boot)
- [JUnit 5 사용자 가이드](https://junit.org/junit5/docs/current/user-guide/)

---

## 라이선스

This project is provided as-is for educational purposes.

---

## 문의

이 프로젝트에 대한 질문이나 제안이 있으시면 이슈를 등록해주세요.
