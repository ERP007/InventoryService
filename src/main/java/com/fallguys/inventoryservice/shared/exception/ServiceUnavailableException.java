package com.fallguys.inventoryservice.shared.exception;

/**
 * 외부 서비스 호출이 기술적으로 실패했을 때의 베이스 예외. 503(SERVICE_UNAVAILABLE)으로 매핑된다.
 * 비즈니스 예외(4xx)가 아니라 시스템성 실패라 BusinessException을 상속하지 않는다.
 * 구체 예외는 이 클래스를 상속하면 별도 핸들러 추가 없이 503으로 처리된다. 원본 원인(cause)은 보존하되 클라이언트엔 비노출한다.
 */
public abstract class ServiceUnavailableException extends RuntimeException {

    private final String code;

    protected ServiceUnavailableException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
