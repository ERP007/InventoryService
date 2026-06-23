package com.fallguys.inventoryservice.stock.domain;

/**
 * 재고 상태. 현재고와 안전재고로부터 파생되며 저장하지 않는다(컬럼 없음).
 */
public enum StockStatus {
    NORMAL,
    LOW;

    /**
     * 현재고·안전재고로 재고 상태를 파생한다.
     * - quantity >= safetyStock → NORMAL (안전재고 이상이면 안전; 안전재고 0이면 재고 0도 정상)
     * - quantity < safetyStock → LOW (안전재고 미만; 재고 0 포함 — '재고 없음'을 '부족'에 편입)
     */
    public static StockStatus of(int quantity, int safetyStock) {
        return quantity >= safetyStock ? NORMAL : LOW;
    }
}
