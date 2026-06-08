package com.fallguys.inventoryservice.stock.domain.query;

import java.time.Instant;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.MovementReason;
import com.fallguys.inventoryservice.stock.domain.MovementType;

/**
 * 재고 이동 이력 목록 조회 전용 읽기 모델. 이동 행에 창고(code·name)를 조인하고 부품명·단위는 이력 자체 스냅샷을 쓴다.
 * sourceRef는 저장된 원천 참조(없으면 null) 그대로이며, 조정의 'ADJ-{id}' 합성은 응답 변환에서 수행한다.
 */
public record MovementSummary(
        Long id,
        Instant occurredAt,
        String sku,
        String itemName,
        ItemUnit itemUnit,
        String warehouseCode,
        String warehouseName,
        int delta,
        MovementType type,
        MovementReason reason,
        String sourceRef,
        String executorEmpNo
) {
}
