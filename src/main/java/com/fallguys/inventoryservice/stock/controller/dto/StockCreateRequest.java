package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.command.CreateStockCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 재고 신규 생성 요청. (지금은 Item 검증 없이) 모든 필드를 사용자가 직접 입력한다.
 * 문자열은 trim 정규화하며, 수량·안전재고는 0 이상 정수다.
 */
public record StockCreateRequest(
        @NotBlank String sku,
        @NotBlank String itemName,
        @NotBlank String warehouseCode,
        @NotNull @PositiveOrZero Integer quantity,
        @NotNull @PositiveOrZero Integer safetyStock
) {

    public StockCreateRequest {
        sku = sku == null ? null : sku.trim();
        itemName = itemName == null ? null : itemName.trim();
        warehouseCode = warehouseCode == null ? null : warehouseCode.trim();
    }

    public CreateStockCommand toCommand() {
        return new CreateStockCommand(sku, itemName, warehouseCode, quantity, safetyStock);
    }
}
