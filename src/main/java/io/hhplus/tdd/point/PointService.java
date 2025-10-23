package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;

import java.util.List;

/**
 * 포인트 관리 서비스
 * 아직 구현되지 않은 상태입니다.
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
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @return 충전 후 포인트 정보
     */
    public UserPoint charge(long userId, long amount) {
        throw new UnsupportedOperationException("아직 구현되지 않음");
    }

    /**
     * 사용자의 포인트를 사용합니다.
     * @param userId 사용자 ID
     * @param amount 사용 금액
     * @return 사용 후 포인트 정보
     */
    public UserPoint use(long userId, long amount) {
        throw new UnsupportedOperationException("아직 구현되지 않음");
    }

    /**
     * 사용자의 현재 포인트를 조회합니다.
     * @param userId 사용자 ID
     * @return 포인트 정보
     */
    public UserPoint getPoint(long userId) {
        throw new UnsupportedOperationException("아직 구현되지 않음");
    }

    /**
     * 사용자의 포인트 거래 내역을 조회합니다.
     * @param userId 사용자 ID
     * @return 거래 내역 목록
     */
    public List<PointHistory> getHistories(long userId) {
        throw new UnsupportedOperationException("아직 구현되지 않음");
    }
}
