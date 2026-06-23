package com.fallguys.inventoryservice.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 수신 이벤트 공통 envelope. payload는 이벤트 종류별로 모양이 달라 JsonNode로 받아 두고, consumer가 타입 payload로 매핑한다.
 * 알 수 없는 필드는 무시한다(producer가 envelope에 필드를 추가해도 깨지지 않도록 — forward-compat).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(
        String eventId,
        String eventType,
        Integer eventVersion,
        String producer,
        String occurredAt,
        String correlationId,
        JsonNode payload
) {
}
