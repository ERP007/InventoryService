package com.fallguys.inventoryservice.stock.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.query.InboundResult;

/**
 * 재고 입고 응답(내부). 처리된(또는 멱등 replay된) 이동 이력 라인들을 담는다. movements는 전부 INBOUND다.
 */
public record InboundResponse(
        String sourceRef,
        String warehouseCode,
        List<InboundMovementResponse> movements
) {

    public static InboundResponse from(InboundResult result) {
        List<InboundMovementResponse> movements = result.movements().stream()
                .map(InboundMovementResponse::from)
                .toList();
        return new InboundResponse(result.sourceRef(), result.warehouseCode(), movements);
    }
}
