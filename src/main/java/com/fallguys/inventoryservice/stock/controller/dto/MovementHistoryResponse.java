package com.fallguys.inventoryservice.stock.controller.dto;

import java.time.Instant;

import com.fallguys.inventoryservice.stock.domain.MovementType;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;

/**
 * sku 상세 패널의 최근 이동 이력 항목. type은 코드(enum name)로 노출한다.
 */
public record MovementHistoryResponse(
        MovementType type,
        int delta,
        String executorEmpNo,
        String executorName, // stock movement db에 사원 이름 반정규화 후 추가
        Instant occurredAt
) {

    public static MovementHistoryResponse from(MovementHistory history) {
        return new MovementHistoryResponse(
                history.type(), history.delta(), history.executorEmpNo(), history.executorName(), history.occurredAt());
    }
}
