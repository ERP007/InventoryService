package com.fallguys.inventoryservice.stock.domain.query;

/**
 * 입고 처리 결과의 라인 항목. 생성된 이동 이력 식별자와 변동량·변동 후 잔량을 담는다.
 */
public record InboundMovement(
        Long movementId,
        String sku,
        int delta,
        int currentQuantity
) {
}
