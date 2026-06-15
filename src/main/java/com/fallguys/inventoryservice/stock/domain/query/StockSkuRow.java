package com.fallguys.inventoryservice.stock.domain.query;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;

/**
 * (sku × warehouse) 단위 재고 행 + 부품명 스냅샷. sku 상세 패널의 창고별 현재고·안전재고 표현에 사용한다.
 * status는 보관하지 않고 표현 계층에서 quantity·safetyStock로 파생한다.
 * 상세 패널은 활성 창고만 노출하므로 비활성 창고 행은 조회 단계에서 제외된다(warehouseActive를 보관하지 않는다).
 * itemActive는 부품(SKU) 활성 여부 — 비활성이면 상세 조회 자체를 막는 데 쓴다(응답엔 노출하지 않음).
 */
public record StockSkuRow(
        String itemName,
        ItemUnit itemUnit,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        int quantity,
        int safetyStock,
        boolean itemActive
) {
}
