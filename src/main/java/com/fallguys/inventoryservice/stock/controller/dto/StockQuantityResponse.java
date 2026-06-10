package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;

/**
 * 재고 일괄 조회의 행 항목. (sku × 창고)의 현재고·안전재고.
 */
public record StockQuantityResponse(
        String sku,
        int quantity,
        int safetyStock
) {

    public static StockQuantityResponse from(StockQuantity quantity) {
        return new StockQuantityResponse(quantity.sku(), quantity.quantity(), quantity.safetyStock());
    }
}
