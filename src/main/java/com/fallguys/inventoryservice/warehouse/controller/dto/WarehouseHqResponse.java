package com.fallguys.inventoryservice.warehouse.controller.dto;

import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;

/**
 * 본사 창고 드롭다운 항목. 선택에 필요한 최소 필드(id·code·name)만 담는다.
 */
public record WarehouseHqResponse(
        Long id,
        String code,
        String name
) {

    public static WarehouseHqResponse from(WarehouseHqSummary summary) {
        return new WarehouseHqResponse(summary.id(), summary.code(), summary.name());
    }
}
