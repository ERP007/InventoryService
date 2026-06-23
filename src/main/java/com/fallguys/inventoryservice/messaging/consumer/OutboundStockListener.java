package com.fallguys.inventoryservice.messaging.consumer;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fallguys.inventoryservice.messaging.config.RabbitTopologyConfig;
import com.fallguys.inventoryservice.messaging.event.EventEnvelope;

/**
 * 출고 명령 큐 리스너. body(JSON)를 envelope+payload로 역직렬화해 consumer에 넘긴다.
 * ack 정책은 컨테이너(AUTO): consumer가 정상 반환하면 ack, 예외를 던지면 재시도(설정 횟수) 후 DLQ.
 * 따라서 "처리(2-TX 커밋) 후 반환 → ack"가 보장되고, 기술 실패만 예외로 전파해 재시도/DLQ로 간다.
 *
 * <p>게이트: autoStartup을 토글(inventory.messaging.async-enabled)로 묶어, 꺼져 있으면 컨테이너를 시작하지 않는다
 * (브로커 없이 부팅·테스트 가능). 토글 ON일 때만 큐를 소비한다.
 */
@Component
public class OutboundStockListener {

    // 메시징 전용 인스턴스(전역 ObjectMapper 빈 미사용 — 웹 JSON 설정과 분리).
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboundStockConsumer consumer;

    public OutboundStockListener(OutboundStockConsumer consumer) {
        this.consumer = consumer;
    }

    @RabbitListener(
            queues = RabbitTopologyConfig.OUTBOUND_SALES_QUEUE,
            autoStartup = "${inventory.messaging.async-enabled:false}")
    public void onMessage(Message message) {
        EventEnvelope envelope = readEnvelope(message);
        OutboundCommandPayload payload = readPayload(envelope);
        consumer.consume(envelope, payload);
    }

    private EventEnvelope readEnvelope(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), EventEnvelope.class);
        } catch (IOException e) {
            throw new MalformedEventException("이벤트 envelope 역직렬화에 실패했습니다.", e);
        }
    }

    private OutboundCommandPayload readPayload(EventEnvelope envelope) {
        if (envelope.payload() == null) {
            throw new MalformedEventException("payload가 비어 있습니다.");
        }
        try {
            return objectMapper.treeToValue(envelope.payload(), OutboundCommandPayload.class);
        } catch (IOException e) {
            throw new MalformedEventException("출고 payload 역직렬화에 실패했습니다.", e);
        }
    }
}
