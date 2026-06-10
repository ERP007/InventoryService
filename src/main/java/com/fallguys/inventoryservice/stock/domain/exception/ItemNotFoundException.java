package com.fallguys.inventoryservice.stock.domain.exception;

import com.fallguys.inventoryservice.shared.exception.ResourceNotFoundException;

/**
 * 입고 신규 재고행 생성 시 참조한 SKU가 Item 마스터에 존재하지 않을 때 발생한다(Item 조회 404 또는 통합 비활성으로 정보 없음). 404로 매핑된다.
 */
public class ItemNotFoundException extends ResourceNotFoundException {

    public ItemNotFoundException(String sku) {
        super(StockErrorCode.ITEM_NOT_FOUND.getCode(), "Item 마스터에서 부품을 찾을 수 없습니다: sku=" + sku);
    }
}
