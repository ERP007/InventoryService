package com.fallguys.inventoryservice.warehouse.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseOption;

/**
 * 창고 선택 목록 조회 응답. 드롭다운 채움 용도라 페이지네이션·정렬 메타가 없다.
 *
 * @param content 활성 창고 항목 목록(0건이면 빈 배열)
 */
public record WarehouseOptionListResponse(
        List<WarehouseOptionResponse> content
) {

    public static WarehouseOptionListResponse from(List<WarehouseOption> options) {
        List<WarehouseOptionResponse> content = options.stream()
                .map(WarehouseOptionResponse::from)
                .toList();
        return new WarehouseOptionListResponse(content);
    }
}
