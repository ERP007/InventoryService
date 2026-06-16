package com.fallguys.inventoryservice.warehouse.domain.exception;

import com.fallguys.inventoryservice.shared.exception.ConflictException;

/**
 * 마지막 활성 본사(HQ) 창고를 비활성화하려 할 때 발생한다. 409(CONFLICT)로 매핑된다.
 * 활성 본사 창고는 SO 출고의 소스라 시스템에 최소 1개는 남아 있어야 한다(현재 상태와 충돌하는 작업).
 */
public class LastActiveHqWarehouseException extends ConflictException {

    public LastActiveHqWarehouseException(String code) {
        super(WarehouseErrorCode.ACTIVE_HQ_REQUIRED.getCode(),
                "마지막 활성 본사 창고는 비활성화할 수 없습니다: " + code);
    }
}
