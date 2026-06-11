package com.fallguys.inventoryservice.stock.domain.query;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;

/**
 * 안전재고 조정 화면의 프리필·수정 결과 읽기 모델. (sku × 창고)의 현재 안전재고와 낙관적 락용 version을 담는다.
 * itemName·itemUnit·quantity(현재고)는 화면 표시·참고용이다.
 */
public record SafetyStockEdit(
        String sku,
        String warehouseCode,
        String itemName,
        ItemUnit itemUnit,
        int quantity,
        int safetyStock,
        Long version
) {
}
