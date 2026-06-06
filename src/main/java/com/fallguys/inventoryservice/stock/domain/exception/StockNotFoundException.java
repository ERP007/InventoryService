package com.fallguys.inventoryservice.stock.domain.exception;

import com.fallguys.inventoryservice.shared.exception.ResourceNotFoundException;

/**
 * 재고를 찾을 수 없거나 접근 권한이 없을 때 발생한다. 404로 매핑된다.
 * 존재 은닉 — 담당 창고가 아닌 코드로의 조회를 "없음"과 동일하게 처리한다(타 창고 존재 노출 방지).
 */
public class StockNotFoundException extends ResourceNotFoundException {

    public StockNotFoundException(String warehouseCode, String sku) {
        super(StockErrorCode.STOCK_NOT_FOUND.getCode(),
                "재고를 찾을 수 없습니다: warehouse=" + warehouseCode + ", sku=" + sku);
    }
}
