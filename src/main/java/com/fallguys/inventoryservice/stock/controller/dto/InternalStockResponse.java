package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;

/**
 * 내부 재고 일괄 조회 응답의 행 항목. (sku × 창고)의 현재고·안전재고.
 */
public record InternalStockResponse(
        String sku,
        int quantity,
        int safetyStock
) {

    public static InternalStockResponse from(StockQuantity quantity) {
        return new InternalStockResponse(quantity.sku(), quantity.quantity(), quantity.safetyStock());
    }
}
