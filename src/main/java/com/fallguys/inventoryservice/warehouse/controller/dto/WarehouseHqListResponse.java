package com.fallguys.inventoryservice.warehouse.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;

/**
 * 본사 창고 목록 조회 응답. 드롭다운 채움 용도라 페이지네이션·정렬 메타가 없다.
 *
 * @param content 본사 창고 항목 목록(0건이면 빈 배열)
 */
public record WarehouseHqListResponse(
        List<WarehouseHqResponse> content
) {

    public static WarehouseHqListResponse from(List<WarehouseHqSummary> summaries) {
        List<WarehouseHqResponse> content = summaries.stream()
                .map(WarehouseHqResponse::from)
                .toList();
        return new WarehouseHqListResponse(content);
    }
}
