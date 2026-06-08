package com.fallguys.inventoryservice.stock.domain.command;

import com.fallguys.inventoryservice.stock.domain.AdjustmentType;
import com.fallguys.inventoryservice.stock.domain.MovementReason;

/**
 * 재고 조정 유스케이스 입력. quantity는 INCREASE/DECREASE면 변동 개수, ADJUST면 실측 잔량(=변동 후 잔량)이다.
 * executorEmpNo·executorName은 컨트롤러가 JWT(employee_no·name)에서 채워 넣는다(이동 이력의 수행자 스냅샷).
 */
public record AdjustStockCommand(
        String sku,
        String warehouseCode,
        AdjustmentType adjustmentType,
        int quantity,
        MovementReason reason,
        String note,
        String executorEmpNo,
        String executorName
) {
}
