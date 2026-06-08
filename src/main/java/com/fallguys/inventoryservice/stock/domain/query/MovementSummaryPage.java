package com.fallguys.inventoryservice.stock.domain.query;

import java.util.List;

/**
 * 이동 이력 페이지 결과(도메인 표현). Spring Data Page를 도메인 밖으로 노출하지 않기 위한 경계 타입이다.
 * page는 1-base이며, hasPrevious/hasNext는 표현 계층에서 page·totalPages로 파생한다.
 */
public record MovementSummaryPage(
        List<MovementSummary> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
