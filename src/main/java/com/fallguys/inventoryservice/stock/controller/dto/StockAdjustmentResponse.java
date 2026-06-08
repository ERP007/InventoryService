package com.fallguys.inventoryservice.stock.controller.dto;

import java.time.Instant;

import com.fallguys.inventoryservice.stock.domain.StockStatus;
import com.fallguys.inventoryservice.stock.domain.query.StockAdjustmentResult;

/**
 * 재고 조정 응답. status는 변동 후 현재고·안전재고에서 파생한다.
 */
public record StockAdjustmentResponse(
        Long movementId,
        Long stockId,
        String sku,
        String warehouseCode,
        int previousQuantity,
        int delta,
        int currentQuantity,
        int safetyStock,
        StockStatus status,
        Instant occurredAt
) {

    public static StockAdjustmentResponse from(StockAdjustmentResult result) {
        return new StockAdjustmentResponse(
                result.movementId(),
                result.stockId(),
                result.sku(),
                result.warehouseCode(),
                result.previousQuantity(),
                result.delta(),
                result.currentQuantity(),
                result.safetyStock(),
                StockStatus.of(result.currentQuantity(), result.safetyStock()),
                result.occurredAt());
    }
}
