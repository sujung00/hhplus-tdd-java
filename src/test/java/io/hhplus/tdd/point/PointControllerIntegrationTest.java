package io.hhplus.tdd.point;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * PointController 통합 테스트
 * Spring Boot 환경에서 REST API의 정상 작동을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PointController 통합 테스트")
class PointControllerIntegrationTest {

    @Autowired
    private PointController pointController;

    private long testUserId;

    private static long testCounter = 0;

    @BeforeEach
    void setUp() {
        testUserId = System.nanoTime() + (testCounter++);
    }

    @Nested
    @DisplayName("포인트 조회 API")
    class GetPointTests {

        @Test
        @DisplayName("존재하지 않는 사용자의 포인트는 0이어야 한다")
        void shouldReturnZeroForNonExistentUser() {
            // when
            UserPoint result = pointController.point(testUserId);

            // then
            assertThat(result)
                    .isNotNull()
                    .extracting(UserPoint::id, UserPoint::point)
                    .containsExactly(testUserId, 0L);
        }

        @Test
        @DisplayName("충전된 포인트를 정확히 조회할 수 있다")
        void shouldReturnCorrectPointAfterCharge() {
            // given
            long chargeAmount = 1000L;
            pointController.charge(testUserId, chargeAmount);

            // when
            UserPoint result = pointController.point(testUserId);

            // then
            assertThat(result.point()).isEqualTo(chargeAmount);
        }
    }

    @Nested
    @DisplayName("거래 내역 조회 API")
    class GetHistoriesTests {

        @Test
        @DisplayName("존재하지 않는 사용자의 거래 내역은 비어있어야 한다")
        void shouldReturnEmptyHistoriesForNonExistentUser() {
            // when
            List<PointHistory> histories = pointController.history(testUserId);

            // then
            assertThat(histories).isEmpty();
        }

        @Test
        @DisplayName("모든 거래 내역을 정확히 조회할 수 있다")
        void shouldReturnAllHistories() {
            // given
            pointController.charge(testUserId, 1000L);
            pointController.use(testUserId, 500L);
            pointController.charge(testUserId, 2000L);

            // when
            List<PointHistory> histories = pointController.history(testUserId);

            // then
            assertThat(histories)
                    .hasSize(3)
                    .extracting(PointHistory::type)
                    .containsExactly(
                            TransactionType.CHARGE,
                            TransactionType.USE,
                            TransactionType.CHARGE
                    );
        }

        @Test
        @DisplayName("거래 내역은 시간순으로 정렬되어야 한다")
        void shouldReturnHistoriesSortedByTime() {
            // given
            pointController.charge(testUserId, 1000L);
            pointController.charge(testUserId, 2000L);
            pointController.use(testUserId, 500L);

            // when
            List<PointHistory> histories = pointController.history(testUserId);

            // then
            for (int i = 0; i < histories.size() - 1; i++) {
                assertThat(histories.get(i).updateMillis())
                        .isLessThanOrEqualTo(histories.get(i + 1).updateMillis());
            }
        }
    }

    @Nested
    @DisplayName("포인트 충전 API")
    class ChargeTests {

        @Test
        @DisplayName("유효한 금액으로 포인트를 충전할 수 있다")
        void shouldChargePointsSuccessfully() {
            // given
            long chargeAmount = 10000L;

            // when
            UserPoint result = pointController.charge(testUserId, chargeAmount);

            // then
            assertThat(result.point()).isEqualTo(chargeAmount);
        }

        @Test
        @DisplayName("포인트를 누적하여 충전할 수 있다")
        void shouldAccumulateCharges() {
            // given
            long charge1 = 5000L;
            long charge2 = 3000L;

            // when
            pointController.charge(testUserId, charge1);
            UserPoint result = pointController.charge(testUserId, charge2);

            // then
            assertThat(result.point()).isEqualTo(charge1 + charge2);
        }

        @Test
        @DisplayName("0 이하의 금액 충전은 실패해야 한다")
        void shouldFailWhenChargingZeroOrNegativeAmount() {
            // when & then
            assertThatThrownBy(() -> pointController.charge(testUserId, 0))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> pointController.charge(testUserId, -1000))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("최소 충전 금액 미만은 실패해야 한다")
        void shouldFailWhenChargingBelowMinAmount() {
            // when & then
            assertThatThrownBy(() -> pointController.charge(testUserId, 50))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("최소");
        }

        @Test
        @DisplayName("최대 포인트를 초과하면 실패해야 한다")
        void shouldFailWhenExceedingMaxPoint() {
            // given
            long existingPoint = 900_000L;
            long chargeAmount = 200_000L;

            pointController.charge(testUserId, existingPoint);

            // when & then
            assertThatThrownBy(() -> pointController.charge(testUserId, chargeAmount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("초과");
        }
    }

    @Nested
    @DisplayName("포인트 사용 API")
    class UseTests {

        @Test
        @DisplayName("충분한 잔고로 포인트를 사용할 수 있다")
        void shouldUsePointsSuccessfully() {
            // given
            long chargeAmount = 10000L;
            long useAmount = 3000L;

            pointController.charge(testUserId, chargeAmount);

            // when
            UserPoint result = pointController.use(testUserId, useAmount);

            // then
            assertThat(result.point()).isEqualTo(chargeAmount - useAmount);
        }

        @Test
        @DisplayName("정확히 전액을 사용할 수 있다")
        void shouldUseExactlyAllPoints() {
            // given
            long amount = 5000L;
            pointController.charge(testUserId, amount);

            // when
            UserPoint result = pointController.use(testUserId, amount);

            // then
            assertThat(result.point()).isEqualTo(0);
        }

        @Test
        @DisplayName("잔고가 부족하면 사용이 실패해야 한다")
        void shouldFailWhenInsufficientBalance() {
            // given
            long chargeAmount = 1000L;
            long useAmount = 2000L;

            pointController.charge(testUserId, chargeAmount);

            // when & then
            assertThatThrownBy(() -> pointController.use(testUserId, useAmount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("부족");
        }

        @Test
        @DisplayName("0 이하의 금액 사용은 실패해야 한다")
        void shouldFailWhenUsingZeroOrNegativeAmount() {
            // given
            pointController.charge(testUserId, 5000L);

            // when & then
            assertThatThrownBy(() -> pointController.use(testUserId, 0))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> pointController.use(testUserId, -1000))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTests {

        private static final int THREAD_COUNT = 10;
        private static final int OPERATIONS_PER_THREAD = 10;

        @Test
        @DisplayName("동일 사용자에 대한 동시 충전은 안전하게 처리되어야 한다")
        void shouldHandleConcurrentChargesCorrectly() throws InterruptedException {
            // given
            long chargeAmount = 1000L;
            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

            // when
            for (int i = 0; i < THREAD_COUNT; i++) {
                executorService.submit(() -> {
                    try {
                        for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                            pointController.charge(testUserId, chargeAmount);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            UserPoint result = pointController.point(testUserId);
            long expectedPoint = chargeAmount * THREAD_COUNT * OPERATIONS_PER_THREAD;
            assertThat(result.point()).isEqualTo(expectedPoint);
        }

        @Test
        @DisplayName("동일 사용자에 대한 동시 사용은 안전하게 처리되어야 한다")
        void shouldHandleConcurrentUsesCorrectly() throws InterruptedException {
            // given
            long totalCharge = 100_000L;
            long useAmount = 1000L;
            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

            pointController.charge(testUserId, totalCharge);

            // when
            AtomicInteger successCount = new AtomicInteger(0);
            for (int i = 0; i < THREAD_COUNT; i++) {
                executorService.submit(() -> {
                    try {
                        for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                            try {
                                pointController.use(testUserId, useAmount);
                                successCount.incrementAndGet();
                            } catch (IllegalArgumentException e) {
                                // 잔고 부족은 정상 상황
                                if (!e.getMessage().contains("부족")) {
                                    throw e;
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            UserPoint result = pointController.point(testUserId);
            long expectedPoint = totalCharge - (successCount.get() * useAmount);
            assertThat(result.point()).isEqualTo(expectedPoint);

            // 거래 내역도 정확해야 함
            List<PointHistory> histories = pointController.history(testUserId);
            long useHistoryCount = histories.stream()
                    .filter(h -> h.type() == TransactionType.USE)
                    .count();
            assertThat(useHistoryCount).isEqualTo(successCount.get());
        }

        @Test
        @DisplayName("동일 사용자에 대한 충전과 사용의 혼합 요청은 안전하게 처리되어야 한다")
        void shouldHandleMixedConcurrentOperationsCorrectly() throws InterruptedException {
            // given
            long chargeAmount = 5000L;
            long useAmount = 1000L;
            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

            // 초기 충전
            pointController.charge(testUserId, 100_000L);

            // when
            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadIndex = i;
                executorService.submit(() -> {
                    try {
                        if (threadIndex % 2 == 0) {
                            // 짝수 스레드: 충전
                            for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                                pointController.charge(testUserId, chargeAmount);
                            }
                        } else {
                            // 홀수 스레드: 사용
                            for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                                try {
                                    pointController.use(testUserId, useAmount);
                                } catch (IllegalArgumentException e) {
                                    if (!e.getMessage().contains("부족")) {
                                        throw e;
                                    }
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            UserPoint result = pointController.point(testUserId);
            assertThat(result.point()).isGreaterThanOrEqualTo(0L);

            // 거래 내역 개수 검증
            List<PointHistory> histories = pointController.history(testUserId);
            assertThat(histories).isNotEmpty();
        }

        @Test
        @DisplayName("서로 다른 사용자의 동시 요청은 상호 간섭이 없어야 한다")
        void shouldNotInterfereWithDifferentUsers() throws InterruptedException {
            // given
            long chargeAmount = 10000L;
            long baseUserId = System.nanoTime();  // 나노초 사용으로 고유성 보장
            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

            // when
            for (int i = 0; i < THREAD_COUNT; i++) {
                final long userId = baseUserId + i;
                executorService.submit(() -> {
                    try {
                        pointController.charge(userId, chargeAmount);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then - 각 사용자의 포인트가 독립적이어야 함
            for (int i = 0; i < THREAD_COUNT; i++) {
                long userId = baseUserId + i;
                UserPoint result = pointController.point(userId);
                assertThat(result.point()).isEqualTo(chargeAmount);
            }
        }
    }
}
