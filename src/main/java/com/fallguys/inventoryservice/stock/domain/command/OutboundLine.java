package com.fallguys.inventoryservice.stock.domain.command;

/**
 * 출고 처리 한 라인. quantity는 출고 수량(양수), sourceLineNo는 원천 문서(SO)의 라인 식별자다.
 */
public record OutboundLine(
        String sku,
        int quantity,
        int sourceLineNo
) {
}
