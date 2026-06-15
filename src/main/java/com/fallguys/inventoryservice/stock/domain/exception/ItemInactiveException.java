package com.fallguys.inventoryservice.stock.domain.exception;

import com.fallguys.inventoryservice.shared.exception.BusinessException;

/**
 * 비활성(active=false) 부품(SKU)의 재고를 조정·입고·출고하려 할 때 발생한다. 400(BusinessException)으로 매핑된다.
 * 아이템 활성 여부는 stock 행에 반정규화되어 있어, 검증은 외부 호출 없이 로컬에서 수행한다.
 */
public class ItemInactiveException extends BusinessException {

    public ItemInactiveException(String sku) {
        super(StockErrorCode.ITEM_INACTIVE.getCode(), "비활성 부품은 처리할 수 없습니다: sku=" + sku);
    }
}
