package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit;

/**
 * 안전재고 조정 모달 프리필 응답. 현재 안전재고와 version(낙관적 락용), 표시용 부품 정보·현재고를 담는다.
 */
public record SafetyStockEditResponse(
        String sku,
        String warehouseCode,
        String itemName,
        ItemUnit itemUnit,
        int quantity,
        int safetyStock,
        Long version
) {

    public static SafetyStockEditResponse from(SafetyStockEdit edit) {
        return new SafetyStockEditResponse(
                edit.sku(), edit.warehouseCode(), edit.itemName(), edit.itemUnit(),
                edit.quantity(), edit.safetyStock(), edit.version());
    }
}
