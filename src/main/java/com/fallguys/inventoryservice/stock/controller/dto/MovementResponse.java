package com.fallguys.inventoryservice.stock.controller.dto;

import java.time.Instant;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.MovementReason;
import com.fallguys.inventoryservice.stock.domain.MovementType;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummary;

/**
 * 재고 이동 이력 목록 항목. type·reason·unit은 코드(enum name)로 노출하고 한글 라벨은 프론트가 매핑한다.
 * unit은 이동 이력 자체 스냅샷(item_unit)에서 가져온다.
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
        ItemUnit unit,
        MovementReason reason,
        String sourceRef,
        String executorEmpNo,
        String executorName
) {

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
                summary.itemUnit(),
                summary.reason(),
                sourceRef,
                summary.executorEmpNo(),
                summary.executorName());
    }
}
