package com.fallguys.inventoryservice.warehouse.domain.exception;

import com.fallguys.inventoryservice.shared.exception.BusinessException;

/**
 * 창고 유형과 소속 지점(branchId)의 정합이 맞지 않을 때 발생한다. 400으로 매핑된다.
 * DEALER는 branchId 필수, HQ는 branchId 불가.
 */
public class WarehouseBranchRuleException extends BusinessException {

    public WarehouseBranchRuleException(String message) {
        super(WarehouseErrorCode.WAREHOUSE_BRANCH_RULE.getCode(), message);
    }
}
