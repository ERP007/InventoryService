package com.fallguys.inventoryservice.stock.domain.query;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;

/**
 * (sku × warehouse) 단위 재고 행 + 부품명 스냅샷. sku 상세 패널의 창고별 현재고·안전재고 표현에 사용한다.
 * status는 보관하지 않고 표현 계층에서 quantity·safetyStock로 파생한다.
 */
public record StockSkuRow(
        String itemName,
        ItemUnit itemUnit,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        int quantity,
        int safetyStock
) {
}
