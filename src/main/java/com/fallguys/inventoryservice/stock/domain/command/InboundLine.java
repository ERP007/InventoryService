package com.fallguys.inventoryservice.stock.domain.command;

/**
 * 입고 처리 한 라인. quantity는 입고 수량(양수), sourceLineNo는 원천 문서(PO/SO)의 라인 식별자다.
 */
public record InboundLine(
        String sku,
        int quantity,
        int sourceLineNo
) {
}
