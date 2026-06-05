package com.fallguys.inventoryservice.warehouse.controller.dto;

import java.time.Instant;

import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

/**
 * 창고 활성 상태 전환 응답. 토글 결과 확인에 필요한 최소 필드만 반환한다.
 * version은 후속 수정·재토글의 낙관적 락에 사용된다.
 */
public record WarehouseActiveResponse(
        Long id,
        boolean active,
        Instant updatedAt,
        Long version
) {

    public static WarehouseActiveResponse from(WarehouseSummaryForEdit detail) {
        return new WarehouseActiveResponse(detail.id(), detail.active(), detail.updatedAt(), detail.version());
    }
}
