package com.fallguys.inventoryservice.warehouse.domain.query;

import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;

import java.time.Instant;

/**
 * 창고 수정 모달 프리필 전용 읽기 모델. 창고 속성에 소속 지점(branchId·branchName)·주소,
 * 그리고 낙관적 락을 위한 version을 합쳐 단건 상세를 표현한다.
 * HQ 유형은 소속 지점이 없으므로 branchId와 branchName이 null이다.
 */
public record WarehouseSummaryForEdit(
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
}
