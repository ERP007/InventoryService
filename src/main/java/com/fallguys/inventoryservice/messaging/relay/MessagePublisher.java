package com.fallguys.inventoryservice.messaging.relay;

/**
 * [추상화] 메시지를 브로커에 발행하고 수신 확정(publisher confirm)까지 기다린다.
 * relay가 이 결과(ack 여부)로 outbox 상태 전환을 결정하므로, 발행/확정의 기술 구현(RabbitMQ)과 분리해 테스트를 쉽게 한다.
 */
public interface MessagePublisher {

    /**
     * payload를 exchange·routingKey로 발행하고 브로커 confirm을 기다린다.
     *
     * @return 브로커가 수신을 ack하면 true, nack·타임아웃이면 false. 발행 자체가 실패(연결 등)하면 예외를 던진다.
     */
    boolean publishConfirmed(String exchange, String routingKey, String eventId, String eventType, String payload);
}
