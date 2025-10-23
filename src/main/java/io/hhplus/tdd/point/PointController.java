package io.hhplus.tdd.point;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 포인트 관련 REST 컨트롤러
 * 사용자의 포인트 조회, 충전, 사용, 거래 내역 조회 API를 제공합니다.
 */
@RestController
@RequestMapping("/point")
public class PointController {

    private static final Logger log = LoggerFactory.getLogger(PointController.class);

    private final PointService pointService;

    @Autowired
    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    /**
     * 특정 사용자의 현재 포인트를 조회합니다.
     *
     * @param id 사용자 ID
     * @return 사용자의 포인트 정보
     */
    @GetMapping("{id}")
    public UserPoint point(@PathVariable long id) {
        log.info("포인트 조회 - userId: {}", id);
        return pointService.getPoint(id);
    }

    /**
     * 특정 사용자의 포인트 거래 내역을 조회합니다.
     *
     * @param id 사용자 ID
     * @return 거래 내역 목록
     */
    @GetMapping("{id}/histories")
    public List<PointHistory> history(@PathVariable long id) {
        log.info("포인트 거래 내역 조회 - userId: {}", id);
        return pointService.getHistories(id);
    }

    /**
     * 특정 사용자의 포인트를 충전합니다.
     *
     * @param id 사용자 ID
     * @param amount 충전 금액
     * @return 충전 후 포인트 정보
     */
    @PatchMapping("{id}/charge")
    public UserPoint charge(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        log.info("포인트 충전 - userId: {}, amount: {}", id, amount);
        return pointService.charge(id, amount);
    }

    /**
     * 특정 사용자의 포인트를 사용합니다.
     *
     * @param id 사용자 ID
     * @param amount 사용 금액
     * @return 사용 후 포인트 정보
     */
    @PatchMapping("{id}/use")
    public UserPoint use(
            @PathVariable long id,
            @RequestBody long amount
    ) {
        log.info("포인트 사용 - userId: {}, amount: {}", id, amount);
        return pointService.use(id, amount);
    }
}
