package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.query.InboundMovement;

/**
 * 입고 응답의 라인 항목. delta는 변동량(양수), currentQuantity는 변동 후 현재고.
 */
public record InboundMovementResponse(
        Long movementId,
        String sku,
        int delta,
        int currentQuantity
) {

    public static InboundMovementResponse from(InboundMovement movement) {
        return new InboundMovementResponse(
                movement.movementId(), movement.sku(), movement.delta(), movement.currentQuantity());
    }
}
