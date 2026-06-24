package com.fallguys.inventoryservice.stock.controller.dto;

import java.time.LocalDate;
import java.util.List;

import com.fallguys.inventoryservice.stock.domain.query.StockActivitySummary;

/**
 * 최근 7일 재고 이동 활동 응답(대시보드 차트). days는 from→to 오름차순 7개로 이동이 없는 날도 0으로 포함한다.
 * 차트는 막대를 일자별 입고·출고·조정 3색 스택으로 그리고, 상단 총건수는 세 합계(totalInbound/Outbound/Adjust)의 합으로 표시한다.
 */
public record StockActivityResponse(
        LocalDate from,
        LocalDate to,
        List<DayActivity> days,
        long totalInbound,
        long totalOutbound,
        long totalAdjust) {

    public record DayActivity(LocalDate date, long inbound, long outbound, long adjust) {
    }

    public static StockActivityResponse from(StockActivitySummary summary) {
        List<DayActivity> days = summary.days().stream()
                .map(day -> new DayActivity(day.date(), day.inbound(), day.outbound(), day.adjust()))
                .toList();
        return new StockActivityResponse(
                summary.from(), summary.to(), days,
                summary.totalInbound(), summary.totalOutbound(), summary.totalAdjust());
    }
}
