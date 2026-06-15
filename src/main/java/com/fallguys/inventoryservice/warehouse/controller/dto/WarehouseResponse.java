package com.fallguys.inventoryservice.warehouse.controller.dto;

import java.time.Instant;

import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;

/**
 * 창고 목록 항목 응답. branchName은 HQ 유형이면 null.
 */
public record WarehouseResponse(
        Long id,
        String code,
        String name,
        WarehouseType type,
        String branchName,
        String address,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static WarehouseResponse from(WarehouseSummary summary) {
        return new WarehouseResponse(
                summary.id(),
                summary.code(),
                summary.name(),
                summary.type(),
                summary.branchName(),
                summary.address(),
                summary.active(),
                summary.createdAt(),
                summary.updatedAt()
        );
    }
}
