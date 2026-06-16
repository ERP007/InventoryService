package com.fallguys.inventoryservice.stock.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.query.ItemStockRow;

/**
 * 부품 마스터 화면의 창고별 현재고 응답. sku와 창고별 재고(stocks[])를 담는다.
 * 재고 행이 없으면 stocks는 빈 배열이다(404가 아님). sku·name·unit 중 name·unit은 프론트가 이미 보유하므로 sku만 반환한다.
 */
public record ItemStockListResponse(
        String sku,
        List<ItemStockResponse> stocks
) {

    public static ItemStockListResponse from(String sku, List<ItemStockRow> rows) {
        List<ItemStockResponse> stocks = rows.stream()
                .map(ItemStockResponse::from)
                .toList();
        return new ItemStockListResponse(sku, stocks);
    }
}
