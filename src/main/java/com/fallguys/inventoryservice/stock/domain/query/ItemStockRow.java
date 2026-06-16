package com.fallguys.inventoryservice.stock.domain.query;

/**
 * 부품 마스터 화면의 "창고별 현재고" 패널용 (sku × warehouse) 재고 행. 최근 수정 순으로 조회된다.
 * status는 보관하지 않고 표현 계층에서 currentStock·safetyStock로 파생한다.
 * 비활성 창고 행은 조회 단계에서 제외되므로 warehouseActive를 보관하지 않는다.
 * 비활성 부품(SKU)도 단순 조회 대상이라 itemActive로 거르지 않는다(보관하지 않음).
 */
public record ItemStockRow(
        String warehouseCode,
        String warehouseName,
        int currentStock,
        int safetyStock
) {
}
