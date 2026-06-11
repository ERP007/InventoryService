package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.command.UpdateSafetyStockCommand;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 안전재고 수정 요청. safetyStock은 변경할 절대값(0 이상), version은 프리필 시점 값(낙관적 락).
 * sku·warehouseCode는 path로 받으므로 바디에 두지 않는다.
 */
public record SafetyStockUpdateRequest(
        @NotNull(message = "안전재고는 필수입니다.")
        @PositiveOrZero(message = "안전재고는 0 이상이어야 합니다.")
        Integer safetyStock,

        @NotNull(message = "version은 필수입니다.")
        Long version
) {

    public UpdateSafetyStockCommand toCommand(String warehouseCode, String sku) {
        return new UpdateSafetyStockCommand(warehouseCode, sku, safetyStock, version);
    }
}
