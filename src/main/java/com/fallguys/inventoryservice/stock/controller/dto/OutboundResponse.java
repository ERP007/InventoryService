package com.fallguys.inventoryservice.stock.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.query.OutboundResult;

/**
 * 재고 출고 응답(내부). 처리된(또는 멱등 replay된) 이동 이력 라인들을 담는다. movements는 전부 OUTBOUND다.
 */
public record OutboundResponse(
        String sourceRef,
        String warehouseCode,
        List<OutboundMovementResponse> movements
) {

    public static OutboundResponse from(OutboundResult result) {
        List<OutboundMovementResponse> movements = result.movements().stream()
                .map(OutboundMovementResponse::from)
                .toList();
        return new OutboundResponse(result.sourceRef(), result.warehouseCode(), movements);
    }
}
