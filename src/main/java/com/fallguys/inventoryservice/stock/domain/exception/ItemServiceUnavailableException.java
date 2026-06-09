package com.fallguys.inventoryservice.stock.domain.exception;

/**
 * 외부 Item 서비스 호출이 기술적으로 실패했을 때(연결 실패·타임아웃·5xx 등) 발생한다(§10).
 * 비즈니스 예외가 아니므로 BusinessException(400)을 상속하지 않는다.
 *
 * <p>상세 패널은 이 예외를 잡아 대분류·중분류를 null로 강등하고, 카테고리가 필수인 흐름(입출고 신규행 등)은
 * 502/503으로 매핑한다(전용 핸들러는 해당 기능 도입 시 추가). 원본 원인(cause)은 보존하되 클라이언트엔 노출하지 않는다.
 */
public class ItemServiceUnavailableException extends RuntimeException {

    public ItemServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
