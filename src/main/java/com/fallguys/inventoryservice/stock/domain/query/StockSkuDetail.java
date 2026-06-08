package com.fallguys.inventoryservice.stock.domain.query;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;

/**
 * sku 상세 패널 조회 결과(도메인 표현). 호출자 범위(Tenancy)로 필터된 창고별 재고와 전체 합계, 최근 이동 이력을 담는다.
 * 창고별 status는 표현 계층에서 quantity·safetyStock로 파생한다.
 */
public record StockSkuDetail(
        String sku,
        String itemName,
        ItemUnit itemUnit,
        int totalQuantity,
        int totalSafetyStock,
        List<StockSkuRow> warehouses,
        List<MovementHistory> history
) {
}
