package com.fallguys.inventoryservice.messaging.consumer.inbound;

import com.fallguys.inventoryservice.messaging.consumer.CommandSource;
import com.fallguys.inventoryservice.messaging.consumer.MalformedEventException;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fallguys.inventoryservice.messaging.config.RabbitTopologyConfig;
import com.fallguys.inventoryservice.messaging.event.EventEnvelope;

/**
 * 입고 명령 큐 리스너. SO 도착(sales)과 PO 입고(procurement) 큐를 각각 소비하며, source만 다르고 처리는 동일하다.
 * ack 정책은 컨테이너(AUTO): consumer가 정상 반환하면 ack, 예외면 재시도(설정 횟수) 후 DLQ.
 * autoStartup을 토글로 묶어 async-enabled=false면 컨테이너를 시작하지 않는다(브로커 없이 부팅·테스트 가능).
 */
@Component
public class InboundStockListener {

    private final ObjectMapper objectMapper = new ObjectMapper(); // 메시징 전용(전역 ObjectMapper 빈 미사용)
    private final InboundStockConsumer consumer;

    public InboundStockListener(InboundStockConsumer consumer) {
        this.consumer = consumer;
    }

    @RabbitListener(
            queues = RabbitTopologyConfig.INBOUND_SALES_QUEUE,
            autoStartup = "${inventory.messaging.async-enabled:false}")
    public void onSalesMessage(Message message) {
        EventEnvelope envelope = readEnvelope(message);
        consumer.consume(envelope, readPayload(envelope), CommandSource.SALES);
    }

    @RabbitListener(
            queues = RabbitTopologyConfig.INBOUND_PROCUREMENT_QUEUE,
            autoStartup = "${inventory.messaging.async-enabled:false}")
    public void onProcurementMessage(Message message) {
        EventEnvelope envelope = readEnvelope(message);
        consumer.consume(envelope, readPayload(envelope), CommandSource.PROCUREMENT);
    }

    private EventEnvelope readEnvelope(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), EventEnvelope.class);
        } catch (IOException e) {
            throw new MalformedEventException("이벤트 envelope 역직렬화에 실패했습니다.", e);
        }
    }

    private InboundCommandPayload readPayload(EventEnvelope envelope) {
        if (envelope.payload() == null) {
            throw new MalformedEventException("payload가 비어 있습니다.");
        }
        try {
            return objectMapper.treeToValue(envelope.payload(), InboundCommandPayload.class);
        } catch (IOException e) {
            throw new MalformedEventException("입고 payload 역직렬화에 실패했습니다.", e);
        }
    }
}
