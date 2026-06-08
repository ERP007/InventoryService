package com.fallguys.inventoryservice.stock.domain.query;

import java.time.Instant;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;

/**
 * 재고 목록 조회 전용 읽기 모델. 재고 속성에 창고(code·name)를 조인해 표현한다.
 * status는 quantity·safetyStock에서 파생되므로 보관하지 않는다(응답에서 계산).
 */
public record StockSummary(
        Long id,
        String sku,
        String itemName,
        ItemUnit itemUnit,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        int quantity,
        int safetyStock,
        Instant lastAdjustedAt
) {
}
