package com.fallguys.inventoryservice.warehouse.domain.exception;

import com.fallguys.inventoryservice.shared.exception.BusinessException;

/**
 * 비활성(active=false) 창고로 입출고를 시도할 때 발생한다. 400(BusinessException)으로 매핑된다.
 */
public class WarehouseInactiveException extends BusinessException {

    public WarehouseInactiveException(String code) {
        super(WarehouseErrorCode.WAREHOUSE_INACTIVE.getCode(), "비활성 창고로는 입출고할 수 없습니다: " + code);
    }
}
