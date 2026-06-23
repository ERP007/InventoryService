package com.fallguys.inventoryservice.stock.domain.query;

/**
 * 재고 KPI 집계 결과(도메인 표현). 모든 카운트는 호출자 범위(Tenancy)로 필터된다.
 * totalSkuCount는 (sku × warehouse) 포지션 총 수이며(이름은 화면 카드 계약을 따른다),
 * lowStockCount는 부족(안전재고 미만, 재고 0 포함) 포지션 수, fulfillmentRate는 안전재고 충족률(정상÷총×100, %),
 * recentAdjustCount는 최근 7일 이동 건수다.
 */
public record StockKpi(
        long totalSkuCount,
        long lowStockCount,
        double fulfillmentRate,
        long recentAdjustCount
) {
}
