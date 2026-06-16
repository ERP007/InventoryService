package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.StockStatus;
import com.fallguys.inventoryservice.stock.domain.query.ItemStockRow;

/**
 * 부품 마스터 화면의 창고별 현재고 항목. status는 현재고·안전재고에서 파생한다.
 */
public record ItemStockResponse(
        String warehouseCode,
        String warehouseName,
        int currentStock,
        int safetyStock,
        StockStatus status
) {

    public static ItemStockResponse from(ItemStockRow row) {
        return new ItemStockResponse(
                row.warehouseCode(),
                row.warehouseName(),
                row.currentStock(),
                row.safetyStock(),
                StockStatus.of(row.currentStock(), row.safetyStock()));
    }
}
