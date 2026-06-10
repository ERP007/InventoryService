package com.fallguys.inventoryservice.warehouse.controller.dto;

import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

/**
 * 내부 서비스용 창고 기본 정보 응답(존재·활성·유형·소속 검증). 표시용 필드는 최소화한다.
 * HQ 유형은 소속 지점이 없으므로 branchName이 null이다.
 */
public record WarehouseInfoResponse(
        Long id,
        String code,
        String name,
        WarehouseType type,
        String branchName,
        boolean active
) {

    public static WarehouseInfoResponse from(WarehouseSummaryForEdit warehouse) {
        return new WarehouseInfoResponse(
                warehouse.id(),
                warehouse.code(),
                warehouse.name(),
                warehouse.type(),
                warehouse.branchName(),
                warehouse.active());
    }
}
