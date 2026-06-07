package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.query.StockKpi;

/**
 * 재고 KPI 응답. 4개 카드 메트릭의 superset(총 포지션·부족·무재고·최근 7일 이동)을 담는다.
 * 정상 수는 totalSkuCount - lowStockCount - noStockCount로 프론트가 유도한다.
 */
public record StockKpiResponse(
        long totalSkuCount,
        long lowStockCount,
        long noStockCount,
        long recentAdjustCount
) {

    public static StockKpiResponse from(StockKpi kpi) {
        return new StockKpiResponse(
                kpi.totalSkuCount(), kpi.lowStockCount(), kpi.noStockCount(), kpi.recentAdjustCount());
    }
}
