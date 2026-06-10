package com.fallguys.inventoryservice.stock.domain.query;

/**
 * (창고 × SKU) 재고 수량 읽기 모델(내부 일괄 조회용). 재고 행이 있는 SKU만 포함된다.
 */
public record StockQuantity(
        String sku,
        int quantity,
        int safetyStock
) {
}
