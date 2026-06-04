package com.fallguys.inventoryservice.shared.exception;

/**
 * 리소스 충돌(중복 등) 비즈니스 예외의 베이스. 409(CONFLICT)로 매핑된다.
 * 구체 충돌 예외는 이 클래스를 상속하면 별도 핸들러 추가 없이 409로 처리된다.
 */
public abstract class ConflictException extends BusinessException {

    protected ConflictException(String code, String message) {
        super(code, message);
    }
}
