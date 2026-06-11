package com.fallguys.inventoryservice.stock.domain.command;

/**
 * 안전재고 수정 유스케이스 입력. (sku × warehouseCode) 행의 safetyStock을 절대값으로 교체한다.
 * version은 프리필 시점 값으로, 그 사이 변경됐으면 낙관적 락 충돌(409)로 거부한다.
 */
public record UpdateSafetyStockCommand(
        String warehouseCode,
        String sku,
        int safetyStock,
        Long version
) {
}
