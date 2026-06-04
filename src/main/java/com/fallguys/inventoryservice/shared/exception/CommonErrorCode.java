package com.fallguys.inventoryservice.shared.exception;

import lombok.Getter;

/**
 * 애그리거트에 종속되지 않는 공통 에러 코드. 형식 검증·시스템 오류 등 횡단 관심사에 사용한다.
 * 애그리거트 고유 에러는 각 애그리거트의 XxxErrorCode를 사용한다.
 */
@Getter
public enum CommonErrorCode {

    INVALID_PARAMETER("INVALID_PARAMETER", "요청 파라미터가 올바르지 않습니다."),
    INTERNAL_ERROR("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");

    private final String code;
    private final String defaultMessage;

    CommonErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
