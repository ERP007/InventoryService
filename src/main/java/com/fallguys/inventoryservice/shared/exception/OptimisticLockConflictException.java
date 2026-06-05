package com.fallguys.inventoryservice.shared.exception;

/**
 * 낙관적 락 충돌(동시 수정)이 발생했을 때 던진다. 409(CONFLICT)로 매핑된다.
 * 클라이언트가 보낸 version이 현재 상태와 달라 갱신을 거부한 경우로, 최신 재조회 후 재시도를 유도한다.
 */
public class OptimisticLockConflictException extends ConflictException {

    public OptimisticLockConflictException(String message) {
        super(CommonErrorCode.OPTIMISTIC_LOCK_CONFLICT.getCode(), message);
    }
}
