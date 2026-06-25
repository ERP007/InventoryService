package com.fallguys.inventoryservice.messaging.activity;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fallguys.inventoryservice.messaging.config.RabbitTopologyConfig;
import com.fallguys.inventoryservice.messaging.outbox.OutboxEvent;
import com.fallguys.inventoryservice.messaging.outbox.OutboxEventJpaDao;
import com.fallguys.inventoryservice.shared.activity.UserActivityAction;
import com.fallguys.inventoryservice.shared.activity.UserActivityRecorder;

import lombok.RequiredArgsConstructor;

/**
 * 사용자 활동 이벤트를 트랜잭셔널 outbox에 적재하는 구현체(기존 outbox·relay 재사용, erp.events로 발행).
 * 호출자(업무 서비스)의 @Transactional에 합류해 비즈니스 변경과 같은 커밋으로 묶이고, relay가 커밋 이후 발행한다.
 *
 * <p>envelope는 user-service 소비 명세(user.activity.occurred)를 따른다. occurredAt은 envelope·payload 동일.
 * 수행자 사번은 현재 JWT(employee_no)에서 가져오며(인증 컨텍스트 없으면 SYSTEM), correlationId는 "INV-"+eventId다.
 * outbox UNIQUE(aggregate_type, aggregate_id, event_type) 충돌을 피하려 aggregate_id를 발생건마다 유니크한 eventId로 둔다
 * (활동 로그는 발생마다 별도 행이며 멱등은 consumer의 eventId inbox가 담당).
 */
@Component
@RequiredArgsConstructor
public class OutboxUserActivityRecorder implements UserActivityRecorder {

    private static final String PRODUCER = "inventory-service";
    private static final int EVENT_VERSION = 1;
    private static final String EVENT_TYPE = "user.activity.occurred";
    private static final String ROUTING_KEY = "user.activity.occurred";
    private static final String AGGREGATE_TYPE = "USER_ACTIVITY";
    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final OutboxEventJpaDao outboxDao;
    // 현재 인증 사용자 사번 제공(JpaAuditingConfig의 employee_no AuditorAware 재사용).
    private final AuditorAware<String> auditorAware;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void record(UserActivityAction action, String title, String content, String status) {
        UUID eventId = UUID.randomUUID();
        String occurredAt = Instant.now().toString();
        String employeeNo = auditorAware.getCurrentAuditor().orElse(SYSTEM_ACTOR);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("employeeNo", employeeNo);
        payload.put("action", action.name());
        payload.put("occurredAt", occurredAt);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("status", status);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", EVENT_TYPE);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("producer", PRODUCER);
        envelope.put("occurredAt", occurredAt);
        envelope.put("correlationId", "INV-" + eventId);
        envelope.put("payload", payload);

        outboxDao.save(OutboxEvent.pending(
                AGGREGATE_TYPE, eventId.toString(), EVENT_TYPE, eventId,
                RabbitTopologyConfig.EVENTS_EXCHANGE, ROUTING_KEY, toJson(envelope)));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("사용자 활동 이벤트 직렬화에 실패했습니다.", e);
        }
    }
}
