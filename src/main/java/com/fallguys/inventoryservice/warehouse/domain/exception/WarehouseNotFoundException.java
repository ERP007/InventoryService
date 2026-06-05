package com.fallguys.inventoryservice.warehouse.domain.exception;

import com.fallguys.inventoryservice.shared.exception.ResourceNotFoundException;

/**
 * 창고가 존재하지 않거나 접근 권한이 없을 때 발생한다. 404로 매핑된다.
 * 존재 은닉 — "없음"과 "소속 외(권한 없음)"를 동일한 응답으로 처리한다.
 */
public class WarehouseNotFoundException extends ResourceNotFoundException {

    public WarehouseNotFoundException(Long id) {
        super(WarehouseErrorCode.WAREHOUSE_NOT_FOUND.getCode(), "창고를 찾을 수 없습니다: " + id);
    }

    public WarehouseNotFoundException(String code) {
        super(WarehouseErrorCode.WAREHOUSE_NOT_FOUND.getCode(), "창고를 찾을 수 없습니다: " + code);
    }
}
