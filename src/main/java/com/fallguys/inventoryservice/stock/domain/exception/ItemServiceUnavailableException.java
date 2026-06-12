package com.fallguys.inventoryservice.stock.domain.exception;

import com.fallguys.inventoryservice.shared.exception.CommonErrorCode;
import com.fallguys.inventoryservice.shared.exception.ServiceUnavailableException;

/**
 * 외부 Item 서비스 호출이 기술적으로 실패했을 때(연결 실패·타임아웃·5xx·단위 계약 불일치) 발생한다(§10). 502(BAD_GATEWAY)로 매핑된다.
 *
 * <p>상세 패널은 이 예외를 잡아 대분류·중분류를 null로 강등하고, 입고 신규행 등 Item 정보가 필수인 흐름은 502로 전파한다.
 * 원본 원인(cause)은 보존하되 클라이언트엔 노출하지 않는다.
 */
public class ItemServiceUnavailableException extends ServiceUnavailableException {

    public ItemServiceUnavailableException(String message, Throwable cause) {
        super(CommonErrorCode.ITEM_SERVICE_UNAVAILABLE.getCode(), message, cause);
    }
}
