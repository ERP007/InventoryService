package com.fallguys.inventoryservice.warehouse.domain.exception;

import com.fallguys.inventoryservice.shared.exception.BusinessException;

/**
 * 창고 코드 변경을 시도했을 때(수정 요청에 code가 포함됐을 때) 발생한다. 400으로 매핑된다.
 * code는 비즈니스 식별자로 불변이다.
 */
public class WarehouseCodeImmutableException extends BusinessException {

    public WarehouseCodeImmutableException() {
        super(WarehouseErrorCode.WAREHOUSE_CODE_IMMUTABLE.getCode(), "창고 코드는 변경할 수 없습니다.");
    }
}
