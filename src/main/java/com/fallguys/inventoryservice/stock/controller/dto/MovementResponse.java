package com.fallguys.inventoryservice.stock.controller.dto;

import java.time.Instant;

import com.fallguys.inventoryservice.stock.domain.MovementReason;
import com.fallguys.inventoryservice.stock.domain.MovementType;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummary;

/**
 * 재고 이동 이력 목록 항목. type·reason은 코드(enum name)로 노출하고 한글 라벨은 프론트가 매핑한다.
 * unit은 현재 임시 고정값("개")이며 추후 Item 마스터의 단위로 대체한다.
 * sourceRef는 원천 참조가 없는 조정 행이면 'ADJ-{id}'로 합성한다.
 */
public record MovementResponse(
        Long id,
        Instant occurredAt,
        String sku,
        String itemName,
        String warehouseCode,
        String warehouseName,
        int delta,
        MovementType type,
        String unit,
        MovementReason reason,
        String sourceRef,
        String executorEmpNo
) {

    private static final String DEFAULT_UNIT = "개";

    public static MovementResponse from(MovementSummary summary) {
        String sourceRef = summary.sourceRef() != null ? summary.sourceRef() : "ADJ-" + summary.id();
        return new MovementResponse(
                summary.id(),
                summary.occurredAt(),
                summary.sku(),
                summary.itemName(),
                summary.warehouseCode(),
                summary.warehouseName(),
                summary.delta(),
                summary.type(),
                DEFAULT_UNIT,
                summary.reason(),
                sourceRef,
                summary.executorEmpNo());
    }
}
