package com.fallguys.inventoryservice.stock.domain.query;

import java.util.List;

/**
 * 재고 입고 처리 결과(도메인 표현). 한 sourceRef·창고에 대해 생성(또는 멱등 replay)된 이동 이력 라인들을 담는다.
 */
public record InboundResult(
        String sourceRef,
        String warehouseCode,
        List<InboundMovement> movements
) {
}
