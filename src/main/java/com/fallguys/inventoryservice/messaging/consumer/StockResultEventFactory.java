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
import com.fallguys.inventoryservice.stock.domain.command.InboundCommand;
import com.fallguys.inventoryservice.stock.domain.command.OutboundCommand;
import com.fallguys.inventoryservice.stock.domain.query.InboundResult;
import com.fallguys.inventoryservice.stock.domain.query.OutboundResult;

/**
 * 출고·입고 결과 이벤트(applied/rejected)를 outbox 행으로 만든다. envelope JSON 전체를 payload에 담고 relay가 그대로 발행한다.
 *
 * outbox 멱등 키 aggregate_id = 처리한 명령의 eventId(시도 단위)다. 같은 SO/PO를 재시도하면 새 명령 eventId라
 * 별도 결과 행으로 발행돼(실패→재시도 시 클라이언트 폴링이 풀린다), 같은 명령의 중복 수신은 inbox(eventId)가 이미 막는다.
 * (sourceRef로 키잉하면 SO당 결과 1행만 허용돼 재시도 거절이 UNIQUE 충돌로 유실됐다.)
 * correlationId=sourceRef(사가 상관키)는 envelope에 그대로 둔다. eventType은 상대 도메인을 붙이지 않고(스키마 동일),
 * routing key에만 상대 도메인(sales/procurement)을 붙인다.
 *
 * movements는 sku·delta·stockAfter(+quantity=|delta|)만 싣는다. sourceLineNo는 결과 DTO에 아직 없어 생략(명세 #6 보류).
 */
@Component
public class StockResultEventFactory {

    private static final String PRODUCER = "inventory-service";
    private static final int EVENT_VERSION = 1;
    private static final String OUTBOUND_AGGREGATE = "STOCK_OUTBOUND";
    private static final String INBOUND_AGGREGATE = "STOCK_INBOUND";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 출고 성공 결과(SUCCEEDED + movements). routing key는 sales 고정(출고는 sales만). aggregateId=처리한 명령 eventId. */
    public OutboxEvent applied(String commandEventId, OutboundResult result) {
        List<Map<String, Object>> movements = result.movements().stream()
                .map(m -> movement(m.sku(), m.delta(), m.currentQuantity())).toList();
        return appliedEvent(OUTBOUND_AGGREGATE, commandEventId, result.sourceRef(),
                "inventory.stock.outbound.applied", "inventory.stock.outbound.applied.sales",
                result.warehouseCode(), movements);
    }

    /** 출고 비즈니스 거절(FAILED + errorCode, retryable=false). */
    public OutboxEvent rejected(String commandEventId, OutboundCommand command, BusinessException exception) {
        return rejectedEvent(OUTBOUND_AGGREGATE, commandEventId, command.sourceRef(),
                "inventory.stock.outbound.rejected", "inventory.stock.outbound.rejected.sales",
                command.warehouseCode(), exception);
    }

    /** 입고 성공 결과. routing key의 상대 도메인은 source(SALES=SO 도착 / PROCUREMENT=PO 입고)로 결정. aggregateId=처리한 명령 eventId. */
    public OutboxEvent inboundApplied(String commandEventId, InboundResult result, CommandSource source) {
        List<Map<String, Object>> movements = result.movements().stream()
                .map(m -> movement(m.sku(), m.delta(), m.currentQuantity())).toList();
        return appliedEvent(INBOUND_AGGREGATE, commandEventId, result.sourceRef(),
                "inventory.stock.inbound.applied", "inventory.stock.inbound.applied." + source.suffix(),
                result.warehouseCode(), movements);
    }

    /** 입고 비즈니스 거절(INSUFFICIENT_STOCK 없음 — WAREHOUSE_*·ITEM_NOT_FOUND·ITEM_INACTIVE 등). */
    public OutboxEvent inboundRejected(String commandEventId, InboundCommand command, CommandSource source,
                                       BusinessException exception) {
        return rejectedEvent(INBOUND_AGGREGATE, commandEventId, command.sourceRef(),
                "inventory.stock.inbound.rejected", "inventory.stock.inbound.rejected." + source.suffix(),
                command.warehouseCode(), exception);
    }

    // ---- shared builders ----

    private OutboxEvent appliedEvent(String aggregateType, String aggregateId, String sourceRef, String eventType,
                                     String routingKey, String warehouseCode, List<Map<String, Object>> movements) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceRef", sourceRef);
        payload.put("warehouseCode", warehouseCode);
        payload.put("status", "SUCCEEDED");
        payload.put("movements", movements);
        return build(aggregateType, aggregateId, sourceRef, eventType, routingKey, payload);
    }

    private OutboxEvent rejectedEvent(String aggregateType, String aggregateId, String sourceRef, String eventType,
                                      String routingKey, String warehouseCode, BusinessException exception) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceRef", sourceRef);
        payload.put("warehouseCode", warehouseCode);
        payload.put("status", "FAILED");
        payload.put("errorCode", exception.getCode());
        payload.put("errorMessage", exception.getMessage());
        payload.put("retryable", false);
        return build(aggregateType, aggregateId, sourceRef, eventType, routingKey, payload);
    }

    private OutboxEvent build(String aggregateType, String aggregateId, String sourceRef, String eventType,
                             String routingKey, Map<String, Object> payload) {
        UUID eventId = UUID.randomUUID();
        String body = toJson(envelope(eventId, eventType, sourceRef, payload));
        return OutboxEvent.pending(aggregateType, aggregateId, eventType, eventId,
                RabbitTopologyConfig.EVENTS_EXCHANGE, routingKey, body);
    }

    private static Map<String, Object> movement(String sku, int delta, int stockAfter) {
        Map<String, Object> mm = new LinkedHashMap<>();
        mm.put("sku", sku);
        mm.put("quantity", Math.abs(delta));
        mm.put("delta", delta);
        mm.put("stockAfter", stockAfter);
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
