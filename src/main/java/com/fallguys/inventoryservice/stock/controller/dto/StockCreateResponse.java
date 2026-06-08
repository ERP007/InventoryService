package com.fallguys.inventoryservice.stock.controller.dto;

import java.time.Instant;

import com.fallguys.inventoryservice.stock.domain.StockStatus;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;

/**
 * 재고 신규 생성 응답. status는 수량·안전재고에서 파생한다.
 */
public record StockCreateResponse(
        Long stockId,
        String sku,
        String warehouseCode,
        int quantity,
        int safetyStock,
        StockStatus status,
        Instant createdAt
) {

    public static StockCreateResponse from(StockCreateResult result) {
        return new StockCreateResponse(
                result.stockId(),
                result.sku(),
                result.warehouseCode(),
                result.quantity(),
                result.safetyStock(),
                StockStatus.of(result.quantity(), result.safetyStock()),
                result.createdAt());
    }
}
