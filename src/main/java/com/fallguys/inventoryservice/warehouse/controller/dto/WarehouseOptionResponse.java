package com.fallguys.inventoryservice.warehouse.controller.dto;

import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseOption;

/**
 * 창고 선택 드롭다운 항목. 선택에 필요한 최소 필드(code·표시명)만 담는다.
 * name은 소속 지점명이며, 본사(HQ) 창고는 소속 지점이 없어 창고명으로 대체한다.
 */
public record WarehouseOptionResponse(
        String code,
        String name
) {

    public static WarehouseOptionResponse from(WarehouseOption option) {
        return new WarehouseOptionResponse(option.code(), option.name());
    }
}
