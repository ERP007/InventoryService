package com.fallguys.inventoryservice.stock.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.query.StockKpi;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockKpiService {

    private static final int RECENT_ADJUST_DAYS = 7;

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;

    /**
     * 대시보드·재고 화면용 KPI를 집계한다. 집계 범위는 파라미터가 아니라 호출자 소속으로 강제된다.
     *
     * 흐름:
     * 1) BRANCH는 자기 창고(tenancy_code)로, ADMIN·HQ는 전사로 범위를 정한다.
     * 2) 범위 내 (sku × warehouse) 포지션의 총/부족/무재고 수를 현재고·안전재고로 계산한다(저장 컬럼 아님).
     * 3) 같은 범위에서 최근 7일(now 기준) 이동 건수를 센다(전 유형 포함).
     *
     * 트랜잭션: 읽기 전용. 외부 호출 없음. 집계가 0이어도 정상 응답(예외 없음).
     */
    @Transactional(readOnly = true)
    public StockKpi getKpi(TenancyType tenancyType, String tenancyCode, Instant now) {
        List<String> scope = tenancyType == TenancyType.BRANCH ? List.of(tenancyCode) : List.of();
        StockStatusCount counts = stockRepository.countByStatus(scope);
        Instant since = now.minus(Duration.ofDays(RECENT_ADJUST_DAYS));
        long recentAdjustCount = stockMovementRepository.countRecent(scope, since);
        return new StockKpi(counts.total(), counts.low(), counts.out(), recentAdjustCount);
    }
}
