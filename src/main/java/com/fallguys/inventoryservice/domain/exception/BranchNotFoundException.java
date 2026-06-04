package com.fallguys.inventoryservice.domain.exception;

/**
 * 참조한 소속 지점(branchId)이 마스터에 존재하지 않을 때 발생한다.
 * 참조 무결성 위반이며 명세상 400으로 매핑된다.
 */
public class BranchNotFoundException extends BusinessException {

    public BranchNotFoundException(Long branchId) {
        super(InventoryErrorCode.BRANCH_NOT_FOUND.getCode(),
                "존재하지 않는 소속 지점입니다: " + branchId);
    }
}
