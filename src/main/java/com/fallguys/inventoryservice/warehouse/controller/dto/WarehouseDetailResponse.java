package com.fallguys.inventoryservice.warehouse.controller.dto;

import java.time.Instant;

import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

/**
 * 창고 단건 상세 응답(수정 모달 프리필용). 목록 응답과 달리 branchId·address·version까지 포함한다.
 * HQ 유형은 branchId와 branchName이 null이다. version은 후속 수정 호출의 낙관적 락에 사용된다.
 */
public record WarehouseDetailResponse(
        Long id,
        String code,
        String name,
        WarehouseType type,
        Long branchId,
        String branchName,
        String address,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {

    public static WarehouseDetailResponse from(WarehouseSummaryForEdit detail) {
        return new WarehouseDetailResponse(
                detail.id(),
                detail.code(),
                detail.name(),
                detail.type(),
                detail.branchId(),
                detail.branchName(),
                detail.address(),
                detail.active(),
                detail.createdAt(),
                detail.updatedAt(),
                detail.version()
        );
    }
}
