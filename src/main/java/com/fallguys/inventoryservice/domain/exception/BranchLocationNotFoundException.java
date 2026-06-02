package com.fallguys.inventoryservice.domain.exception;

public class BranchLocationNotFoundException extends BusinessException {

    public BranchLocationNotFoundException(Long branchId) {
        super(
            InventoryErrorCode.BRANCH_LOCATION_NOT_FOUND.getCode(),
            "지점을 찾을 수 없습니다. id=" + branchId
        );
    }
}
