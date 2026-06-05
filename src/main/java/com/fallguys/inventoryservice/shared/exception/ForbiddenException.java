package com.fallguys.inventoryservice.shared.exception;

/**
 * 인가 실패(권한 없음) 비즈니스 예외. 403(FORBIDDEN)으로 매핑된다.
 * Role이 허용 목록 밖이거나, 토큰에서 Role을 판별할 수 없을 때(클레임 누락·형식 오류) 던진다.
 * (미인증 401은 게이트웨이/리소스 서버 필터가 담당하며 본 예외 범위가 아니다.)
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(CommonErrorCode.FORBIDDEN.getCode(), CommonErrorCode.FORBIDDEN.getDefaultMessage());
    }
}
