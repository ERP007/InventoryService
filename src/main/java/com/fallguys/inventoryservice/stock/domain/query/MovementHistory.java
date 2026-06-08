package com.fallguys.inventoryservice.stock.domain.query;

import java.time.Instant;

import com.fallguys.inventoryservice.stock.domain.MovementType;

/**
 * sku 상세 패널의 최근 이동 이력 항목(요약). delta는 부호 있는 변동량이다.
 */
public record MovementHistory(
        MovementType type,
        int delta,
        String executorEmpNo,
        Instant occurredAt
) {
}
