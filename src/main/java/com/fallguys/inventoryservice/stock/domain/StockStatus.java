package com.fallguys.inventoryservice.stock.domain;

/**
 * 재고 상태. 현재고와 안전재고로부터 파생되며 저장하지 않는다(컬럼 없음).
 */
public enum StockStatus {
    NORMAL,
    LOW,
    OUT;

    /**
     * 현재고·안전재고로 재고 상태를 파생한다.
     * - quantity == 0 → OUT
     * - quantity >= safetyStock → NORMAL (안전재고 이상이면 안전)
     * - 0 < quantity < safetyStock → LOW (안전재고 미만)
     */
    public static StockStatus of(int quantity, int safetyStock) {
        if (quantity == 0) {
            return OUT;
        }
        if (quantity >= safetyStock) {
            return NORMAL;
        }
        return LOW;
    }
}
