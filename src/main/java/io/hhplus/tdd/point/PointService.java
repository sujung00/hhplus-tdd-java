package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 포인트 관리 서비스
 * 사용자의 포인트 충전, 사용, 조회 및 거래 내역 관리를 담당합니다.
 * 동시성 제어를 위해 사용자별 ReentrantLock을 사용합니다.
 */
@Service
public class PointService {

    // 포인트 정책 상수
    private static final long MAX_POINT = 1_000_000L;      // 최대 잔고: 100만 포인트
    private static final long MIN_CHARGE_AMOUNT = 100L;    // 최소 충전 금액: 100
    private static final long MAX_CHARGE_AMOUNT = 1_000_000L;  // 최대 충전 금액: 100만
    private static final long MIN_USE_AMOUNT = 100L;       // 최소 사용 금액: 100
    private static final long MAX_USE_AMOUNT = 1_000_000L; // 최대 사용 금액: 100만
    private static final long LOCK_TIMEOUT_MILLIS = 5000L; // 락 획득 타임아웃: 5초

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    // 사용자별 동시성 제어를 위한 ReentrantLock
    private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    /**
     * 사용자의 포인트를 충전합니다.
     *
     * @param userId 사용자 ID
     * @param amount 충전 금액
     * @return 충전 후 포인트 정보
     * @throws IllegalArgumentException 정책 위반 시
     * @throws InterruptedException 락 획득 중단 시
     */
    public UserPoint charge(long userId, long amount) {
        // 기본 금액 검증
        validateChargeAmount(amount);

        // 사용자별 락 획득
        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());

        try {
            if (!lock.tryLock(LOCK_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("포인트 충전 중 타임아웃이 발생했습니다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("포인트 충전이 중단되었습니다", e);
        }

        try {
            // 현재 사용자의 포인트 조회 (없으면 0으로 초기화)
            UserPoint currentPoint = userPointTable.selectById(userId);
            long newAmount = currentPoint.point() + amount;

            // 최대 잔고 검증
            validateMaxPoint(newAmount);

            // 포인트 업데이트
            UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newAmount);

            // 거래 내역 저장
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());

            return updatedPoint;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 사용자의 포인트를 사용합니다.
     *
     * @param userId 사용자 ID
     * @param amount 사용 금액
     * @return 사용 후 포인트 정보
     * @throws IllegalArgumentException 정책 위반 시
     * @throws InterruptedException 락 획득 중단 시
     */
    public UserPoint use(long userId, long amount) {
        // 기본 금액 검증
        validateUseAmount(amount);

        // 사용자별 락 획득
        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());

        try {
            if (!lock.tryLock(LOCK_TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("포인트 사용 중 타임아웃이 발생했습니다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("포인트 사용이 중단되었습니다", e);
        }

        try {
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
        } finally {
            lock.unlock();
        }
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
     * 충전 금액의 유효성을 검증합니다.
     *
     * @param amount 충전 금액
     * @throws IllegalArgumentException 정책 위반 시
     */
    private void validateChargeAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다");
        }
        if (amount < MIN_CHARGE_AMOUNT) {
            throw new IllegalArgumentException("충전 금액은 최소 " + MIN_CHARGE_AMOUNT + "원 이상이어야 합니다");
        }
        if (amount > MAX_CHARGE_AMOUNT) {
            throw new IllegalArgumentException("충전 금액은 최대 " + MAX_CHARGE_AMOUNT + "원 이하여야 합니다");
        }
    }

    /**
     * 사용 금액의 유효성을 검증합니다.
     *
     * @param amount 사용 금액
     * @throws IllegalArgumentException 정책 위반 시
     */
    private void validateUseAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("사용 금액은 0보다 커야 합니다");
        }
        if (amount < MIN_USE_AMOUNT) {
            throw new IllegalArgumentException("사용 금액은 최소 " + MIN_USE_AMOUNT + "원 이상이어야 합니다");
        }
        if (amount > MAX_USE_AMOUNT) {
            throw new IllegalArgumentException("사용 금액은 최대 " + MAX_USE_AMOUNT + "원 이하여야 합니다");
        }
    }

    /**
     * 최대 포인트 한도를 검증합니다.
     *
     * @param point 검증할 포인트
     * @throws IllegalArgumentException 최대 포인트 초과 시
     */
    private void validateMaxPoint(long point) {
        if (point > MAX_POINT) {
            throw new IllegalArgumentException("포인트는 최대 " + MAX_POINT + "원을 초과할 수 없습니다");
        }
    }
}
