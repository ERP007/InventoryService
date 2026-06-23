package com.fallguys.inventoryservice.messaging.consumer;

/**
 * 메시지를 이해할 수 없거나 payload가 계약 위반(역직렬화 실패·필수값 누락·수량 ≤ 0 등)일 때 던진다.
 * BusinessException이 아니므로 rejected 이벤트를 만들지 않고, 재시도해도 풀리지 않는 poison으로 보아 DLQ로 보낸다.
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message) {
        super(message);
    }

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
