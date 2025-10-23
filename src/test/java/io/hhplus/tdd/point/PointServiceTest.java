package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PointService 단위 테스트")
class PointServiceTest {

    private PointService pointService;
    private UserPointTable userPointTable;
    private PointHistoryTable pointHistoryTable;

    @BeforeEach
    void setUp() {
        userPointTable = new UserPointTable();
        pointHistoryTable = new PointHistoryTable();
        pointService = new PointService(userPointTable, pointHistoryTable);
    }

    @Nested
    @DisplayName("charge - 포인트 충전")
    class ChargeTests {

        @Test
        @DisplayName("존재하지 않는 사용자는 새로 생성되어야 한다")
        void shouldCreateNewUserWhenCharging() {
            // given
            long userId = 1L;
            long amount = 1000L;

            // when
            UserPoint result = pointService.charge(userId, amount);

            // then
            assertThat(result)
                    .isNotNull()
                    .extracting(UserPoint::id, UserPoint::point)
                    .containsExactly(userId, amount);
        }

        @Test
        @DisplayName("기존 사용자는 포인트가 누적되어야 한다")
        void shouldAccumulatePointsForExistingUser() {
            // given
            long userId = 1L;
            long firstAmount = 1000L;
            long secondAmount = 2000L;

            // when
            pointService.charge(userId, firstAmount);
            UserPoint result = pointService.charge(userId, secondAmount);

            // then
            assertThat(result)
                    .isNotNull()
                    .extracting(UserPoint::id, UserPoint::point)
                    .containsExactly(userId, firstAmount + secondAmount);
        }

        @Test
        @DisplayName("0 이하 금액 충전 시 IllegalArgumentException 예외 발생")
        void shouldThrowExceptionWhenChargingWithZeroOrNegativeAmount() {
            // given
            long userId = 1L;

            // when & then
            assertThatThrownBy(() -> pointService.charge(userId, 0))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> pointService.charge(userId, -1000))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("여러 번의 충전 시 모든 거래가 누적되어야 한다")
        void shouldAccumulateMultipleCharges() {
            // given
            long userId = 1L;

            // when
            pointService.charge(userId, 1000L);
            pointService.charge(userId, 2000L);
            UserPoint result = pointService.charge(userId, 3000L);

            // then
            assertThat(result.point()).isEqualTo(6000L);
        }

        @Test
        @DisplayName("충전 후 거래 내역이 저장되어야 한다")
        void shouldSaveChargeHistoryAfterCharging() {
            // given
            long userId = 1L;
            long amount = 1000L;

            // when
            pointService.charge(userId, amount);
            List<PointHistory> histories = pointService.getHistories(userId);

            // then
            assertThat(histories)
                    .isNotEmpty()
                    .hasSize(1)
                    .allMatch(h -> h.type() == TransactionType.CHARGE)
                    .allMatch(h -> h.amount() == amount)
                    .allMatch(h -> h.userId() == userId);
        }
    }

    @Nested
    @DisplayName("use - 포인트 사용")
    class UseTests {

        @Test
        @DisplayName("사용 금액만큼 차감되어야 한다")
        void shouldDeductPointsWhenUsing() {
            // given
            long userId = 1L;
            long chargeAmount = 5000L;
            long useAmount = 2000L;

            pointService.charge(userId, chargeAmount);

            // when
            UserPoint result = pointService.use(userId, useAmount);

            // then
            assertThat(result)
                    .isNotNull()
                    .extracting(UserPoint::id, UserPoint::point)
                    .containsExactly(userId, chargeAmount - useAmount);
        }

        @Test
        @DisplayName("잔고가 부족하면 IllegalArgumentException 예외 발생")
        void shouldThrowExceptionWhenInsufficientBalance() {
            // given
            long userId = 1L;
            long chargeAmount = 1000L;
            long useAmount = 2000L;

            pointService.charge(userId, chargeAmount);

            // when & then
            assertThatThrownBy(() -> pointService.use(userId, useAmount))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("0 이하 금액 사용 시 IllegalArgumentException 예외 발생")
        void shouldThrowExceptionWhenUsingWithZeroOrNegativeAmount() {
            // given
            long userId = 1L;
            long chargeAmount = 5000L;

            pointService.charge(userId, chargeAmount);

            // when & then
            assertThatThrownBy(() -> pointService.use(userId, 0))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> pointService.use(userId, -1000))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("정확히 전액을 사용할 수 있어야 한다")
        void shouldUseExactlyAllPoints() {
            // given
            long userId = 1L;
            long amount = 1000L;

            pointService.charge(userId, amount);

            // when
            UserPoint result = pointService.use(userId, amount);

            // then
            assertThat(result.point()).isEqualTo(0);
        }

        @Test
        @DisplayName("여러 번의 사용 시 정확히 계산되어야 한다")
        void shouldCalculateCorrectlyAfterMultipleUses() {
            // given
            long userId = 1L;
            long chargeAmount = 10000L;
            long use1 = 3000L;
            long use2 = 2000L;

            pointService.charge(userId, chargeAmount);

            // when
            pointService.use(userId, use1);
            UserPoint result = pointService.use(userId, use2);

            // then
            assertThat(result.point()).isEqualTo(chargeAmount - use1 - use2);
        }

        @Test
        @DisplayName("사용 후 거래 내역이 저장되어야 한다")
        void shouldSaveUseHistoryAfterUsing() {
            // given
            long userId = 1L;
            long chargeAmount = 5000L;
            long useAmount = 2000L;

            pointService.charge(userId, chargeAmount);

            // when
            pointService.use(userId, useAmount);
            List<PointHistory> histories = pointService.getHistories(userId);

            // then
            assertThat(histories)
                    .hasSize(2)
                    .filteredOn(h -> h.type() == TransactionType.USE)
                    .hasSize(1)
                    .allMatch(h -> h.amount() == useAmount);
        }
    }

    @Nested
    @DisplayName("getPoint - 포인트 잔액 조회")
    class GetPointTests {

        @Test
        @DisplayName("사용자의 현재 잔액을 반환해야 한다")
        void shouldReturnCurrentBalance() {
            // given
            long userId = 1L;
            long amount = 1000L;

            pointService.charge(userId, amount);

            // when
            UserPoint result = pointService.getPoint(userId);

            // then
            assertThat(result)
                    .isNotNull()
                    .extracting(UserPoint::id, UserPoint::point)
                    .containsExactly(userId, amount);
        }

        @Test
        @DisplayName("존재하지 않는 사용자는 0원으로 초기화된 상태를 반환해야 한다")
        void shouldReturnZeroForNonExistentUser() {
            // given
            long nonExistentUserId = 999L;

            // when
            UserPoint result = pointService.getPoint(nonExistentUserId);

            // then
            assertThat(result)
                    .isNotNull()
                    .extracting(UserPoint::id, UserPoint::point)
                    .containsExactly(nonExistentUserId, 0L);
        }

        @Test
        @DisplayName("충전과 사용을 반영한 정확한 잔액을 반환해야 한다")
        void shouldReturnAccurateBalanceAfterChargeAndUse() {
            // given
            long userId = 1L;
            long chargeAmount = 10000L;
            long useAmount1 = 3000L;
            long useAmount2 = 2000L;

            pointService.charge(userId, chargeAmount);
            pointService.use(userId, useAmount1);
            pointService.use(userId, useAmount2);

            // when
            UserPoint result = pointService.getPoint(userId);

            // then
            assertThat(result.point())
                    .isEqualTo(chargeAmount - useAmount1 - useAmount2);
        }

        @Test
        @DisplayName("여러 번의 거래 후에도 정확한 잔액을 반환해야 한다")
        void shouldReturnCorrectBalanceAfterMultipleTransactions() {
            // given
            long userId = 1L;

            pointService.charge(userId, 5000L);
            pointService.use(userId, 1000L);
            pointService.charge(userId, 3000L);
            pointService.use(userId, 2000L);

            // when
            UserPoint result = pointService.getPoint(userId);

            // then
            assertThat(result.point()).isEqualTo(5000 - 1000 + 3000 - 2000);
        }
    }

    @Nested
    @DisplayName("getHistories - 포인트 거래 내역 조회")
    class GetHistoriesTests {

        @Test
        @DisplayName("사용자의 모든 거래 내역이 반환되어야 한다")
        void shouldReturnAllTransactionHistories() {
            // given
            long userId = 1L;

            pointService.charge(userId, 1000L);
            pointService.use(userId, 500L);
            pointService.charge(userId, 2000L);

            // when
            List<PointHistory> histories = pointService.getHistories(userId);

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
            long userId = 1L;

            pointService.charge(userId, 1000L);
            pointService.charge(userId, 2000L);
            pointService.use(userId, 500L);

            // when
            List<PointHistory> histories = pointService.getHistories(userId);

            // then
            assertThat(histories)
                    .hasSize(3)
                    .isSortedAccordingTo((h1, h2) ->
                            Long.compare(h1.updateMillis(), h2.updateMillis())
                    );
        }

        @Test
        @DisplayName("존재하지 않는 사용자의 거래 내역은 비어있어야 한다")
        void shouldReturnEmptyListForNonExistentUser() {
            // given
            long nonExistentUserId = 999L;

            // when
            List<PointHistory> histories = pointService.getHistories(nonExistentUserId);

            // then
            assertThat(histories).isEmpty();
        }

        @Test
        @DisplayName("거래 내역에는 정확한 거래 유형이 기록되어야 한다")
        void shouldRecordCorrectTransactionTypes() {
            // given
            long userId = 1L;

            pointService.charge(userId, 1000L);
            pointService.charge(userId, 500L);
            pointService.use(userId, 300L);
            pointService.use(userId, 200L);

            // when
            List<PointHistory> histories = pointService.getHistories(userId);

            // then
            long chargeCount = histories.stream()
                    .filter(h -> h.type() == TransactionType.CHARGE)
                    .count();
            long useCount = histories.stream()
                    .filter(h -> h.type() == TransactionType.USE)
                    .count();

            assertThat(chargeCount).isEqualTo(2);
            assertThat(useCount).isEqualTo(2);
        }

        @Test
        @DisplayName("거래 내역에는 정확한 금액이 기록되어야 한다")
        void shouldRecordCorrectAmounts() {
            // given
            long userId = 1L;
            long chargeAmount = 1000L;
            long useAmount = 500L;

            pointService.charge(userId, chargeAmount);
            pointService.use(userId, useAmount);

            // when
            List<PointHistory> histories = pointService.getHistories(userId);

            // then
            assertThat(histories)
                    .hasSize(2)
                    .extracting(PointHistory::amount)
                    .containsExactly(chargeAmount, useAmount);
        }
    }
}
