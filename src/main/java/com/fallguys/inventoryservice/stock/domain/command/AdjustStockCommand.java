package com.fallguys.inventoryservice.stock.domain.command;

import com.fallguys.inventoryservice.stock.domain.AdjustmentType;
import com.fallguys.inventoryservice.stock.domain.MovementReason;

/**
 * 재고 조정 유스케이스 입력. quantity는 INCREASE/DECREASE면 변동 개수, ADJUST면 실측 잔량(=변동 후 잔량)이다.
 * executorEmpNo는 컨트롤러가 JWT(employee_no)에서 채워 넣는다.
 */
public record AdjustStockCommand(
        String sku,
        String warehouseCode,
        AdjustmentType adjustmentType,
        int quantity,
        MovementReason reason,
        String memo,
        String executorEmpNo
) {
}
