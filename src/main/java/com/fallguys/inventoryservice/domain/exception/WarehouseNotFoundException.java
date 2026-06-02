package com.fallguys.inventoryservice.domain.exception;

public class WarehouseNotFoundException extends BusinessException {

    public WarehouseNotFoundException(Long warehouseId) {
        super(
            InventoryErrorCode.WAREHOUSE_NOT_FOUND.getCode(),
            "창고를 찾을 수 없습니다. id=" + warehouseId
        );
    }

    public WarehouseNotFoundException(String code) {
        super(
            InventoryErrorCode.WAREHOUSE_NOT_FOUND.getCode(),
            "창고를 찾을 수 없습니다. code=" + code
        );
    }
}
