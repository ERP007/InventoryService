package com.fallguys.inventoryservice.stock.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.query.DailyActivity;
import com.fallguys.inventoryservice.stock.domain.query.DailyMovementCount;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.StockActivitySummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;

    /**
     * 재고 이동 이력을 조회한다. Tenancy에 따라 조회 범위가 다르다.
     *
     * 흐름:
     * 1) BRANCH 사용자는 자기 창고(tenancy_code)로만 한정한다 — 요청의 창고 필터를 자기 창고로 강제 교체.
     * 2) ADMIN·HQ는 전사 범위로 요청 조건(창고 다중 필터 포함)을 그대로 사용한다.
     * 3) 검증된 조건으로 영속성에서 페이지 조회한다(기간은 어댑터가 KST 일자→Instant 구간으로 변환).
     *
     * 트랜잭션: 읽기 전용. 외부 호출 없음. 매칭 0건이면 빈 페이지.
     */
    private static final int ACTIVITY_DAYS = 7;

    @Transactional(readOnly = true)
    public MovementSummaryPage search(MovementSearchQuery query, TenancyType tenancyType, String tenancyCode) {
        MovementSearchQuery effective = tenancyType == TenancyType.BRANCH
                ? query.withWarehouseCodes(List.of(tenancyCode))
                : query;
        return stockMovementRepository.search(effective);
    }

    /**
     * 대시보드 "최근 7일 활동" 차트용 일자별 입고·출고·조정 건수를 집계한다. 집계 범위는 호출자 소속으로 강제된다.
     *
     * 흐름:
     * 1) BRANCH는 자기 창고(tenancy_code)로, ADMIN·HQ는 전사로 범위를 정한다.
     * 2) [today−6, today] 7일(KST) 구간의 (일자 × 유형) 건수를 영속성에서 받는다(이동이 없는 날은 결과에 없음).
     * 3) 유형을 입고(INBOUND)·출고(OUTBOUND)·조정(INCREASE/DECREASE/ADJUST)으로 접고, 7일 달력에 0으로 채워 정렬·합산한다.
     *
     * 트랜잭션: 읽기 전용. 외부 호출 없음. 이동이 0건이어도 7일 0값으로 정상 응답한다.
     */
    @Transactional(readOnly = true)
    public StockActivitySummary getRecentActivity(TenancyType tenancyType, String tenancyCode, LocalDate today) {
        List<String> scope = tenancyType == TenancyType.BRANCH ? List.of(tenancyCode) : List.of();
        LocalDate from = today.minusDays(ACTIVITY_DAYS - 1L);
        List<DailyMovementCount> counts = stockMovementRepository.countDailyByType(scope, from, today);

        // 일자별 [입고, 출고, 조정] 누적
        Map<LocalDate, long[]> byDate = new HashMap<>();
        for (DailyMovementCount count : counts) {
            long[] slot = byDate.computeIfAbsent(count.date(), key -> new long[3]);
            slot[categoryIndex(count.type())] += count.count();
        }

        // 빈 날도 0으로 채워 from→today 오름차순 7개를 만들고 합계를 낸다.
        List<DailyActivity> days = new ArrayList<>(ACTIVITY_DAYS);
        long totalInbound = 0;
        long totalOutbound = 0;
        long totalAdjust = 0;
        for (int offset = 0; offset < ACTIVITY_DAYS; offset++) {
            LocalDate date = from.plusDays(offset);
            long[] slot = byDate.getOrDefault(date, new long[3]);
            days.add(new DailyActivity(date, slot[0], slot[1], slot[2]));
            totalInbound += slot[0];
            totalOutbound += slot[1];
            totalAdjust += slot[2];
        }
        return new StockActivitySummary(from, today, days, totalInbound, totalOutbound, totalAdjust);
    }

    /** 이동 유형을 활동 카테고리 슬롯 인덱스로 매핑한다(0 입고, 1 출고, 2 조정). */
    private static int categoryIndex(MovementType type) {
        return switch (type) {
            case INBOUND -> 0;
            case OUTBOUND -> 1;
            case INCREASE, DECREASE, ADJUST -> 2;
        };
    }
}
