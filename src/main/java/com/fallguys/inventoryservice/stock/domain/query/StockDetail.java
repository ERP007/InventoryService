package com.fallguys.inventoryservice.stock.domain.query;

/**
 * (창고 × 부품) 단건 재고 조회 결과(SO 발주 라인 표시용). 재고 행이 없으면 quantity·safetyStock 0으로 채워진다.
 */
public record StockDetail(
        String warehouseCode,
        String sku,
        int quantity,
        int safetyStock
) {
}
