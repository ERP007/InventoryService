package com.fallguys.inventoryservice.stock.domain.query;

import java.time.Instant;

/**
 * 재고 생성 결과 읽기 모델. 저장 후 창고 코드를 조인해 응답 구성에 필요한 필드만 담는다.
 * status는 quantity·safetyStock에서 파생되므로 보관하지 않는다.
 */
public record StockCreateResult(
        Long stockId,
        String sku,
        String warehouseCode,
        int quantity,
        int safetyStock,
        Instant createdAt
) {
}
