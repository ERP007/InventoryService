package com.fallguys.inventoryservice.warehouse.domain.query;

/**
 * 본사 창고 드롭다운 전용 슬림 읽기 모델(id·code·name). SO 출고 창고 선택 UI 채움용.
 */
public record WarehouseHqSummary(
        Long id,
        String code,
        String name
) {
}
