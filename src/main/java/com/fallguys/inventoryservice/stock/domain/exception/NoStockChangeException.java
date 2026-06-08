package com.fallguys.inventoryservice.stock.domain.exception;

import com.fallguys.inventoryservice.shared.exception.BusinessException;

/**
 * 조정 결과 변동량(delta)이 0이라 남길 이력이 없을 때 발생한다(주로 ADJUST에서 실측=현재고). 400으로 매핑된다.
 */
public class NoStockChangeException extends BusinessException {

    public NoStockChangeException(String sku) {
        super(StockErrorCode.NO_STOCK_CHANGE.getCode(), "변동이 없어 조정할 수 없습니다: sku=" + sku);
    }
}
