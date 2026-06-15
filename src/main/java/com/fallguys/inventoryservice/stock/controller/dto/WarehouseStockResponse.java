package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.StockStatus;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;

/**
 * sku 상세 패널의 창고별 재고 항목. status는 현재고·안전재고에서 파생한다.
 */
public record WarehouseStockResponse(
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        boolean warehouseActive,
        int quantity,
        int safetyStock,
        StockStatus status
) {

    public static WarehouseStockResponse from(StockSkuRow row) {
        return new WarehouseStockResponse(
                row.warehouseId(),
                row.warehouseCode(),
                row.warehouseName(),
                row.warehouseActive(),
                row.quantity(),
                row.safetyStock(),
                StockStatus.of(row.quantity(), row.safetyStock()));
    }
}
