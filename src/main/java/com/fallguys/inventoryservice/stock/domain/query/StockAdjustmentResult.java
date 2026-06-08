package com.fallguys.inventoryservice.stock.domain.query;

import java.time.Instant;

/**
 * 재고 조정 결과(도메인 표현). 변동 전/후 잔량과 변동량, 생성된 이동 이력 식별자·시각을 담는다.
 * status는 표현 계층에서 currentQuantity·safetyStock로 파생한다.
 */
public record StockAdjustmentResult(
        Long movementId,
        Long stockId,
        String sku,
        String warehouseCode,
        int previousQuantity,
        int delta,
        int currentQuantity,
        int safetyStock,
        Instant occurredAt
) {
}
