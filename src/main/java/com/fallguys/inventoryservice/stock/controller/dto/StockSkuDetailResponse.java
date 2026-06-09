package com.fallguys.inventoryservice.stock.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuDetail;

/**
 * sku 상세 패널 응답. 창고별 재고(warehouse[])와 전체 합계, 최근 이동 이력(history[])을 포함한다.
 * majorCategory(대분류)·middleCategory(중분류)는 외부 Item 서비스 조회 결과이며, 통합 비활성/실패 시 null이다.
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
                detail.sku(), detail.itemName(), detail.itemUnit(),
                detail.majorCategory(), detail.middleCategory(),
                detail.totalQuantity(), detail.totalSafetyStock(), warehouse, history);
    }
}
