package com.fallguys.inventoryservice.stock.controller.dto;

import com.fallguys.inventoryservice.stock.domain.query.OutboundMovement;

/**
 * 출고 응답의 라인 항목. delta는 변동량(음수), currentQuantity는 변동 후 현재고.
 */
public record OutboundMovementResponse(
        Long movementId,
        String sku,
        int delta,
        int currentQuantity
) {

    public static OutboundMovementResponse from(OutboundMovement movement) {
        return new OutboundMovementResponse(
                movement.movementId(), movement.sku(), movement.delta(), movement.currentQuantity());
    }
}
