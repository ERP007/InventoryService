package com.fallguys.inventoryservice.stock.domain.exception;

import com.fallguys.inventoryservice.shared.exception.ConflictException;

/**
 * 차감량이 가용 재고를 초과해 현재고가 음수가 되는 경우 발생한다(음수 재고 금지). 409로 매핑된다.
 */
public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(String sku, int currentQuantity, int delta) {
        super(StockErrorCode.INSUFFICIENT_STOCK.getCode(),
                "가용 재고를 초과해 차감할 수 없습니다: sku=" + sku + ", 현재고=" + currentQuantity + ", 변동=" + delta);
    }
}
