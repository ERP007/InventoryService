package com.fallguys.inventoryservice.shared.exception;

/**
 * 리소스를 찾을 수 없을 때의 비즈니스 예외 베이스. 404(NOT_FOUND)로 매핑된다.
 * 존재 은닉이 필요한 경우(권한 외 접근 등)도 "없음"과 동일하게 이 예외로 표현한다.
 * 구체 예외는 이 클래스를 상속하면 별도 핸들러 추가 없이 404로 처리된다.
 */
public abstract class ResourceNotFoundException extends BusinessException {

    protected ResourceNotFoundException(String code, String message) {
        super(code, message);
    }
}
