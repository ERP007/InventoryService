package com.fallguys.inventoryservice.warehouse.domain.query;

import java.time.Instant;

import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;

/**
 * 창고 목록 조회 전용 읽기 모델. 창고 속성에 소속 지점명(branchName)을 합쳐 조회 결과를 표현한다.
 * HQ 유형은 소속 지점이 없으므로 branchName이 null이다.
 */
public record WarehouseSummary(
        Long id,
        String code,
        String name,
        WarehouseType type,
        String branchName,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
