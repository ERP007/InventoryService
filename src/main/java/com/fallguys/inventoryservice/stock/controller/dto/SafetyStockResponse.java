package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit;

/**
 * 안전재고 수정 응답. 수정 결과 확인에 필요한 최소 필드와 version(후속 재수정의 낙관적 락용)을 반환한다.
 */
public record SafetyStockResponse(
        String sku,
        String warehouseCode,
        int safetyStock,
        Long version
) {

    public static SafetyStockResponse from(SafetyStockEdit edit) {
        return new SafetyStockResponse(edit.sku(), edit.warehouseCode(), edit.safetyStock(), edit.version());
    }
}
