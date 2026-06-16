package com.fallguys.inventoryservice.warehouse.domain.query;

/**
 * 창고 선택 드롭다운 전용 슬림 읽기 모델(code·표시명). 창고 선택 UI 채움용.
 * name은 소속 지점명이며, 소속 지점이 없는 본사(HQ) 창고는 창고명으로 대체한다.
 */
public record WarehouseOption(
        String code,
        String name
) {
}
