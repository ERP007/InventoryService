package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.AdjustmentType;
import com.fallguys.inventoryservice.stock.domain.MovementReason;
import com.fallguys.inventoryservice.stock.domain.command.AdjustStockCommand;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 재고 조정 요청. adjustmentType은 INCREASE/DECREASE/ADJUST.
 * INCREASE/DECREASE는 quantity에 변동 개수(1 이상), ADJUST는 quantity에 실측 잔량(0 이상, =변동 후 잔량)을 넣는다.
 */
public record StockAdjustmentRequest(
        @NotBlank String sku,
        @NotBlank String warehouseCode,
        @NotNull AdjustmentType adjustmentType,
        @NotNull @PositiveOrZero Integer quantity,
        @NotNull MovementReason reason,
        String memo
) {

    public StockAdjustmentRequest {
        sku = sku == null ? null : sku.trim();
        warehouseCode = warehouseCode == null ? null : warehouseCode.trim();
        memo = (memo == null || memo.isBlank()) ? null : memo.trim();
    }

    /**
     * INCREASE/DECREASE는 1 이상이어야 한다(0·음수는 INVALID_PARAMETER). ADJUST는 0을 허용한다(@PositiveOrZero가 음수 차단).
     * 검증 전용 파생 메서드이므로 요청 스키마/역직렬화에는 노출하지 않는다(@JsonIgnore). HV는 @JsonIgnore와 무관하게 평가한다.
     */
    @AssertTrue(message = "증가·감소 수량은 1 이상이어야 합니다.")
    @JsonIgnore
    public boolean isAdjustmentQuantityValid() {
        if (adjustmentType == null || quantity == null) {
            return true;
        }
        return adjustmentType == AdjustmentType.ADJUST || quantity >= 1;
    }

    public AdjustStockCommand toCommand(String executorEmpNo) {
        return new AdjustStockCommand(sku, warehouseCode, adjustmentType, quantity, reason, memo, executorEmpNo);
    }
}
