package com.fallguys.inventoryservice.warehouse.domain.exception;

import com.fallguys.inventoryservice.shared.exception.ConflictException;

/**
 * 등록하려는 창고 코드가 이미 존재할 때 발생한다. 409(CONFLICT)로 매핑된다.
 * 창고 코드는 시스템 내 유일한 비즈니스 식별자다.
 */
public class WarehouseCodeDuplicateException extends ConflictException {

    public WarehouseCodeDuplicateException(String code) {
        super(WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE.getCode(),
                "이미 존재하는 창고 코드입니다: " + code);
    }
}
