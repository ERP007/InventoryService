package com.fallguys.inventoryservice.stock.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;

/**
 * 재고 이동 이력 목록 조회 응답. page는 1-base이며 hasPrevious/hasNext는 page·totalPages에서 파생한다.
 */
public record MovementListResponse(
        List<MovementResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {

    public static MovementListResponse from(MovementSummaryPage page) {
        List<MovementResponse> content = page.content().stream()
                .map(MovementResponse::from)
                .toList();
        return new MovementListResponse(
                content,
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.page() > 1,
                page.page() < page.totalPages());
    }
}
