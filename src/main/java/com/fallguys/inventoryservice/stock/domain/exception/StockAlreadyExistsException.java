package com.fallguys.inventoryservice.stock.domain.exception;

import com.fallguys.inventoryservice.shared.exception.ConflictException;

/**
 * 동일 (sku × warehouse) 재고가 이미 존재할 때 발생한다. 409(CONFLICT)로 매핑된다.
 * 입출고 흐름 밖의 신규 생성에서만 검사하며, 기존 재고는 조정 유스케이스를 사용해야 한다.
 */
public class StockAlreadyExistsException extends ConflictException {

    public StockAlreadyExistsException(String sku, String warehouseCode) {
        super(StockErrorCode.STOCK_ALREADY_EXISTS.getCode(),
                "이미 존재하는 재고입니다: sku=" + sku + ", warehouse=" + warehouseCode);
    }
}
