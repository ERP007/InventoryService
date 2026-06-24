package com.fallguys.inventoryservice.stock.domain.query;

import java.time.LocalDate;
import java.util.List;

/**
 * 최근 7일 재고 이동 활동 집계(대시보드 차트). days는 from→to 오름차순 7개이며 이동이 없는 날도 0으로 포함한다.
 * 합계(totalInbound/Outbound/Adjust)는 days의 카테고리별 합이며, 차트 상단 총건수는 세 합의 합이다.
 */
public record StockActivitySummary(
        LocalDate from,
        LocalDate to,
        List<DailyActivity> days,
        long totalInbound,
        long totalOutbound,
        long totalAdjust) {
}
