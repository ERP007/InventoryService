package com.fallguys.inventoryservice.warehouse.domain.exception;

import com.fallguys.inventoryservice.shared.exception.ConflictException;

/**
 * 등록·수정하려는 소속 지점이 이미 다른 창고에 할당돼 있을 때 발생한다. 409(CONFLICT)로 매핑된다.
 * 소속 지점과 창고는 1:1 매핑이다.
 */
public class BranchAlreadyAssignedException extends ConflictException {

    public BranchAlreadyAssignedException(Long branchId) {
        super(WarehouseErrorCode.BRANCH_ALREADY_ASSIGNED.getCode(),
                "이미 다른 창고에 할당된 소속 지점입니다: branchId=" + branchId);
    }
}
