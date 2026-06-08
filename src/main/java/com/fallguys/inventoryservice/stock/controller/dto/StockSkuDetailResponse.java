package com.fallguys.inventoryservice.stock.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuDetail;

/**
 * sku 상세 패널 응답. 창고별 재고(warehouse[])와 전체 합계, 최근 이동 이력(history[])을 포함한다.
 * majorCategory(대분류)·middleCategory(중분류)는 Item 마스터 소관이라 추후 internal 조회로 채운다(현재는 null).
 */
public record StockSkuDetailResponse(
        String sku,
        String itemName,
        ItemUnit itemUnit,
        String majorCategory,
        String middleCategory,
        int totalQuantity,
        int totalSafetyStock,
        List<WarehouseStockResponse> warehouse,
        List<MovementHistoryResponse> history
) {

    public static StockSkuDetailResponse from(StockSkuDetail detail) {
        List<WarehouseStockResponse> warehouse = detail.warehouses().stream()
                .map(WarehouseStockResponse::from)
                .toList();
        List<MovementHistoryResponse> history = detail.history().stream()
                .map(MovementHistoryResponse::from)
                .toList();
        return new StockSkuDetailResponse(
                detail.sku(), detail.itemName(), detail.itemUnit(), null, null,
                detail.totalQuantity(), detail.totalSafetyStock(), warehouse, history);
    }
}
