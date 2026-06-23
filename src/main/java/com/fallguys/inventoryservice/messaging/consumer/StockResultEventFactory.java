package com.fallguys.inventoryservice.messaging.consumer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fallguys.inventoryservice.messaging.config.RabbitTopologyConfig;
import com.fallguys.inventoryservice.messaging.outbox.OutboxEvent;
import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.stock.domain.command.OutboundCommand;
import com.fallguys.inventoryservice.stock.domain.query.OutboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.OutboundResult;

/**
 * 출고 결과 이벤트(applied/rejected)를 outbox 행으로 만든다. envelope JSON 전체를 payload에 담고, relay가 그대로 발행한다.
 * correlationId는 sourceRef(SO code)로 둔다. aggregate=(STOCK_OUTBOUND, sourceRef)라 outbox UNIQUE가 결과 1건을 보장한다.
 *
 * <p>movements는 현재 OutboundMovement가 가진 sku·delta·stockAfter(+quantity=|delta|)만 싣는다.
 * sourceLineNo는 결과 DTO에 아직 없어 생략한다(명세 검토 #6 보류 항목 — 결과 매핑 확장 시 추가).
 */
@Component
public class StockResultEventFactory {

    private static final String PRODUCER = "inventory-service";
    private static final int EVENT_VERSION = 1;
    private static final String AGGREGATE_TYPE = "STOCK_OUTBOUND";
    private static final String APPLIED_EVENT_TYPE = "inventory.stock.outbound.applied";
    private static final String REJECTED_EVENT_TYPE = "inventory.stock.outbound.rejected";
    private static final String APPLIED_ROUTING_KEY = "inventory.stock.outbound.applied.sales";
    private static final String REJECTED_ROUTING_KEY = "inventory.stock.outbound.rejected.sales";

    // 메시징 전용 인스턴스. payload에 java.time 객체를 직접 싣지 않으므로(시각은 문자열로 변환) 기본 매퍼로 충분하고,
    // 전역 ObjectMapper 빈을 만들지 않아 웹 계층의 JSON 변환 설정에 영향을 주지 않는다.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 출고 성공 결과(SUCCEEDED + movements)를 outbox 행으로 만든다. */
    public OutboxEvent applied(OutboundResult result) {
        List<Map<String, Object>> movements = result.movements().stream()
                .map(StockResultEventFactory::movement)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceRef", result.sourceRef());
        payload.put("warehouseCode", result.warehouseCode());
        payload.put("status", "SUCCEEDED");
        payload.put("movements", movements);

        UUID eventId = UUID.randomUUID();
        String body = toJson(envelope(eventId, APPLIED_EVENT_TYPE, result.sourceRef(), payload));
        return OutboxEvent.pending(AGGREGATE_TYPE, result.sourceRef(), APPLIED_EVENT_TYPE, eventId,
                RabbitTopologyConfig.EVENTS_EXCHANGE, APPLIED_ROUTING_KEY, body);
    }

    /** 출고 비즈니스 거절(FAILED + errorCode)을 outbox 행으로 만든다. retryable=false(재시도해도 풀리지 않는 업무 거절). */
    public OutboxEvent rejected(OutboundCommand command, BusinessException exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceRef", command.sourceRef());
        payload.put("warehouseCode", command.warehouseCode());
        payload.put("status", "FAILED");
        payload.put("errorCode", exception.getCode());
        payload.put("errorMessage", exception.getMessage());
        payload.put("retryable", false);

        UUID eventId = UUID.randomUUID();
        String body = toJson(envelope(eventId, REJECTED_EVENT_TYPE, command.sourceRef(), payload));
        return OutboxEvent.pending(AGGREGATE_TYPE, command.sourceRef(), REJECTED_EVENT_TYPE, eventId,
                RabbitTopologyConfig.EVENTS_EXCHANGE, REJECTED_ROUTING_KEY, body);
    }

    private static Map<String, Object> movement(OutboundMovement m) {
        Map<String, Object> mm = new LinkedHashMap<>();
        mm.put("sku", m.sku());
        mm.put("quantity", Math.abs(m.delta()));
        mm.put("delta", m.delta());
        mm.put("stockAfter", m.currentQuantity());
        return mm;
    }

    private static Map<String, Object> envelope(UUID eventId, String eventType, String correlationId, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("producer", PRODUCER);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("correlationId", correlationId);
        envelope.put("payload", payload);
        return envelope;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("결과 이벤트 직렬화에 실패했습니다.", e);
        }
    }
}
