package com.fallguys.inventoryservice.messaging.consumer;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fallguys.inventoryservice.messaging.config.RabbitTopologyConfig;
import com.fallguys.inventoryservice.messaging.event.EventEnvelope;

/**
 * 품목 마스터 스냅샷 큐 리스너(item-service → inventory). erp.events / item.master.snapshot.changed 를 소비한다.
 * 결과 이벤트가 없으므로 정상 처리 후 ack, 기술 실패는 재시도/DLQ. autoStartup을 토글로 묶는다.
 */
@Component
public class ItemSnapshotListener {

    private final ObjectMapper objectMapper = new ObjectMapper(); // 메시징 전용(전역 ObjectMapper 빈 미사용)
    private final ItemSnapshotConsumer consumer;

    public ItemSnapshotListener(ItemSnapshotConsumer consumer) {
        this.consumer = consumer;
    }

    @RabbitListener(
            queues = RabbitTopologyConfig.ITEM_SNAPSHOT_QUEUE,
            autoStartup = "${inventory.messaging.async-enabled:false}")
    public void onMessage(Message message) {
        EventEnvelope envelope = readEnvelope(message);
        ItemSnapshotPayload payload = readPayload(envelope);
        payload.validate();
        consumer.consume(envelope, payload);
    }

    private EventEnvelope readEnvelope(Message message) {
        try {
            return objectMapper.readValue(message.getBody(), EventEnvelope.class);
        } catch (IOException e) {
            throw new MalformedEventException("이벤트 envelope 역직렬화에 실패했습니다.", e);
        }
    }

    private ItemSnapshotPayload readPayload(EventEnvelope envelope) {
        if (envelope.payload() == null) {
            throw new MalformedEventException("payload가 비어 있습니다.");
        }
        try {
            return objectMapper.treeToValue(envelope.payload(), ItemSnapshotPayload.class);
        } catch (IOException e) {
            throw new MalformedEventException("item snapshot payload 역직렬화에 실패했습니다.", e);
        }
    }
}
