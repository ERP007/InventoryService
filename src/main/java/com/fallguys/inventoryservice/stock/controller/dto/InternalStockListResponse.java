package com.fallguys.inventoryservice.stock.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;

/**
 * 내부 재고 일괄 조회 응답. 한 창고에 대한 요청 SKU들의 현재고·안전재고를 담는다.
 * 재고 행이 없는 (sku×창고)는 stocks에서 생략된다(호출 측이 0으로 간주).
 */
public record InternalStockListResponse(
        String warehouseCode,
        List<InternalStockResponse> stocks
) {

    public static InternalStockListResponse from(String warehouseCode, List<StockQuantity> quantities) {
        List<InternalStockResponse> stocks = quantities.stream()
                .map(InternalStockResponse::from)
                .toList();
        return new InternalStockListResponse(warehouseCode, stocks);
    }
}
