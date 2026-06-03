package com.fallguys.inventoryservice.domain.exception;

import lombok.Getter;

/**
 * 도메인 비즈니스 예외의 베이스. 프레임워크에 의존하지 않으며 code + message만 보유한다.
 * HTTP 변환은 controller 계층의 GlobalExceptionHandler가 전담한다.
 */
@Getter
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
