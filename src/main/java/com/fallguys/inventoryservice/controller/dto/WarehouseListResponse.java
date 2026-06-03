package com.fallguys.inventoryservice.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

/**
 * 창고 목록 조회 응답. 페이지네이션이 없으므로 totalElements는 content 개수와 같다.
 *
 * @param content       창고 항목 목록(매칭 0건이면 빈 배열)
 * @param totalElements 전체 매칭 개수
 * @param sort          적용된 정렬(Spring Data 네이티브 포맷, 예: "code,asc")
 */
public record WarehouseListResponse(
        List<WarehouseResponse> content,
        long totalElements,
        String sort
) {

    public static WarehouseListResponse from(List<WarehouseSummary> summaries, String sort) {
        List<WarehouseResponse> content = summaries.stream()
                .map(WarehouseResponse::from)
                .toList();
        return new WarehouseListResponse(content, content.size(), sort);
    }
}
