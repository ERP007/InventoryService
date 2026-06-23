package com.fallguys.inventoryservice.messaging.relay;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 발행 구현. correlated publisher confirm으로 "브로커 수신"을 확인한다.
 * confirm ack=true는 "Inventory(consumer) 처리 완료"가 아니라 "브로커가 메시지를 받았다"는 의미다(소비 성공과 무관).
 * 중요 메타데이터는 body(payload)에 두고, header에는 라우팅/추적용 eventType·messageId만 둔다.
 */
@Component
public class RabbitMessagePublisher implements MessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final long confirmTimeoutMs;

    public RabbitMessagePublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${inventory.messaging.relay.confirm-timeout-ms:5000}") long confirmTimeoutMs) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    @Override
    public boolean publishConfirmed(String exchange, String routingKey, String eventId, String eventType, String payload) {
        CorrelationData correlation = new CorrelationData(eventId);
        Message message = MessageBuilder
                .withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setMessageId(eventId)
                .setHeader("eventType", eventType)
                .build();
        rabbitTemplate.send(exchange, routingKey, message, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            return confirm != null && confirm.ack();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }
}
