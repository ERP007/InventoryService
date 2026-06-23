package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.query.StockKpi;

/**
 * 재고 KPI 응답. 4개 카드 메트릭의 superset(총 포지션·부족·안전재고 충족률·최근 7일 이동)을 담는다.
 * fulfillmentRate는 안전재고 충족률(정상÷총×100, 소수 1자리, %)이며, 정상 수는 totalSkuCount - lowStockCount로 프론트가 유도한다.
 */
public record StockKpiResponse(
        long totalSkuCount,
        long lowStockCount,
        double fulfillmentRate,
        long recentAdjustCount
) {

    public static StockKpiResponse from(StockKpi kpi) {
        return new StockKpiResponse(
                kpi.totalSkuCount(), kpi.lowStockCount(), kpi.fulfillmentRate(), kpi.recentAdjustCount());
    }
}
