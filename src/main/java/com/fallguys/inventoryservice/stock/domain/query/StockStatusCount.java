package com.fallguys.inventoryservice.stock.domain.query;

/**
 * 호출자 범위로 필터된 (sku × warehouse) 포지션의 상태별 개수. KPI 집계에 사용한다.
 * total은 전체 포지션 수이며 low(부족 = 안전재고 미만, 재고 0 포함)는 현재고·안전재고로 파생한 부분집합 수다(정상 = total - low).
 */
public record StockStatusCount(
        long total,
        long low
) {
}
