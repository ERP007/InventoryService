package com.fallguys.inventoryservice.stock.controller.dto;

import java.time.Instant;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.StockStatus;
import com.fallguys.inventoryservice.stock.domain.query.StockSummary;

/**
 * 재고 목록 항목. status는 수량·안전재고에서 파생한다.
 */
public record StockResponse(
        Long id,
        String sku,
        String itemName,
        ItemUnit itemUnit,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        boolean warehouseActive,
        int quantity,
        int safetyStock,
        StockStatus status,
        Instant lastAdjustedAt
) {

    public static StockResponse from(StockSummary summary) {
        return new StockResponse(
                summary.id(),
                summary.sku(),
                summary.itemName(),
                summary.itemUnit(),
                summary.warehouseId(),
                summary.warehouseCode(),
                summary.warehouseName(),
                summary.warehouseActive(),
                summary.quantity(),
                summary.safetyStock(),
                StockStatus.of(summary.quantity(), summary.safetyStock()),
                summary.lastAdjustedAt());
    }
}
