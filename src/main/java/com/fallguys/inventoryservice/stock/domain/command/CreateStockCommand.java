package com.fallguys.inventoryservice.stock.domain.command;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;

/**
 * 재고 신규 생성 유스케이스 입력. 모든 필드는 표현 계층(@Valid)에서 형식 검증을 마친 상태로 들어온다.
 * warehouseCode는 서비스에서 실제 창고 id로 해석된다.
 */
public record CreateStockCommand(
        String sku,
        String itemName,
        ItemUnit itemUnit,
        String warehouseCode,
        int quantity,
        int safetyStock
) {
}
