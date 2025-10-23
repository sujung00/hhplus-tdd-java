package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;

import java.util.List;

/**
 * 포인트 관리 서비스
 * 사용자의 포인트 충전, 사용, 조회 및 거래 내역 관리를 담당합니다.
 */
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    /**
     * 사용자의 포인트를 충전합니다.
     *
     * @param userId 사용자 ID
     * @param amount 충전 금액 (0 초과)
     * @return 충전 후 포인트 정보
     * @throws IllegalArgumentException amount가 0 이하인 경우
     */
    public UserPoint charge(long userId, long amount) {
        validateAmount(amount, "충전 금액");

        // 현재 사용자의 포인트 조회 (없으면 0으로 초기화)
        UserPoint currentPoint = userPointTable.selectById(userId);
        long newAmount = currentPoint.point() + amount;

        // 포인트 업데이트
        UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newAmount);

        // 거래 내역 저장
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());

        return updatedPoint;
    }

    /**
     * 사용자의 포인트를 사용합니다.
     *
     * @param userId 사용자 ID
     * @param amount 사용 금액 (0 초과)
     * @return 사용 후 포인트 정보
     * @throws IllegalArgumentException amount가 0 이하이거나 잔고가 부족한 경우
     */
    public UserPoint use(long userId, long amount) {
        validateAmount(amount, "사용 금액");

        // 현재 사용자의 포인트 조회
        UserPoint currentPoint = userPointTable.selectById(userId);

        // 잔고 검증
        if (currentPoint.point() < amount) {
            throw new IllegalArgumentException("잔고가 부족합니다. 현재 잔액: " + currentPoint.point() + ", 사용 금액: " + amount);
        }

        long newAmount = currentPoint.point() - amount;

        // 포인트 업데이트
        UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newAmount);

        // 거래 내역 저장
        pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());

        return updatedPoint;
    }

    /**
     * 사용자의 현재 포인트를 조회합니다.
     * 존재하지 않는 사용자는 0원으로 초기화됩니다.
     *
     * @param userId 사용자 ID
     * @return 포인트 정보
     */
    public UserPoint getPoint(long userId) {
        return userPointTable.selectById(userId);
    }

    /**
     * 사용자의 포인트 거래 내역을 조회합니다.
     * 거래 내역은 시간순으로 정렬되어 반환됩니다.
     *
     * @param userId 사용자 ID
     * @return 거래 내역 목록 (시간순)
     */
    public List<PointHistory> getHistories(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    /**
     * 금액의 유효성을 검증합니다.
     *
     * @param amount 검증할 금액
     * @param fieldName 필드명 (에러 메시지용)
     * @throws IllegalArgumentException amount가 0 이하인 경우
     */
    private void validateAmount(long amount, String fieldName) {
        if (amount <= 0) {
            throw new IllegalArgumentException(fieldName + "은 0보다 커야 합니다");
        }
    }
}
