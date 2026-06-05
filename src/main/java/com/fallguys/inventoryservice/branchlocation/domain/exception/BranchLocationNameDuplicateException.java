package com.fallguys.inventoryservice.branchlocation.domain.exception;

import com.fallguys.inventoryservice.shared.exception.ConflictException;

/**
 * 등록하려는 지점명이 이미 존재할 때 발생한다. 409(CONFLICT)로 매핑된다.
 * 지점명은 시스템 내 유일해야 한다.
 */
public class BranchLocationNameDuplicateException extends ConflictException {

    public BranchLocationNameDuplicateException(String name) {
        super(BranchLocationErrorCode.BRANCH_LOCATION_NAME_DUPLICATE.getCode(),
                "이미 존재하는 지점명입니다: " + name);
    }
}
