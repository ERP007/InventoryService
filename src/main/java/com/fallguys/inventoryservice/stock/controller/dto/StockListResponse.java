package com.fallguys.inventoryservice.stock.controller.dto;

import java.util.List;

import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

/**
 * 재고 목록 조회 응답. page는 1-base이며 hasPrevious/hasNext는 page·totalPages에서 파생한다.
 */
public record StockListResponse(
        List<StockResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {

    public static StockListResponse from(StockSummaryPage page) {
        List<StockResponse> content = page.content().stream()
                .map(StockResponse::from)
                .toList();
        return new StockListResponse(
                content,
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.page() > 1,
                page.page() < page.totalPages());
    }
}
