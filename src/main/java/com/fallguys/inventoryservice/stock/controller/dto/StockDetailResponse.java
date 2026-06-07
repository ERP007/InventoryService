package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.query.StockDetail;

/**
 * (창고 × 부품) 단건 재고 응답(SO 발주 라인 표시용). 재고 행이 없으면 quantity·safetyStock 0이다.
 */
public record StockDetailResponse(
        String warehouseCode,
        String sku,
        int quantity,
        int safetyStock
) {

    public static StockDetailResponse from(StockDetail detail) {
        return new StockDetailResponse(
                detail.warehouseCode(), detail.sku(), detail.quantity(), detail.safetyStock());
    }
}
