package com.fallguys.inventoryservice.messaging.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fallguys.inventoryservice.config.JpaAuditingConfig;
import com.fallguys.inventoryservice.messaging.outbox.OutboxEvent;
import com.fallguys.inventoryservice.messaging.outbox.OutboxEventJpaDao;
import com.fallguys.inventoryservice.messaging.outbox.OutboxStatus;
import com.fallguys.inventoryservice.shared.activity.UserActivityAction;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class OutboxUserActivityRecorderTest {

    @Autowired
    private OutboxEventJpaDao outboxDao;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void record는_user_activity_envelope를_outbox에_PENDING으로_적재한다() throws Exception {
        OutboxUserActivityRecorder recorder =
                new OutboxUserActivityRecorder(outboxDao, () -> Optional.of("ADMIN002"));

        recorder.record(UserActivityAction.STOCK_ADJUSTED, "엔진오일 필터", "HMC-EN-00214", "-3");

        List<OutboxEvent> all = outboxDao.findAll();
        assertThat(all).hasSize(1);
        OutboxEvent saved = all.get(0);
        assertThat(saved.getExchange()).isEqualTo("erp.events");
        assertThat(saved.getRoutingKey()).isEqualTo("user.activity.occurred");
        assertThat(saved.getEventType()).isEqualTo("user.activity.occurred");
        assertThat(saved.getAggregateType()).isEqualTo("USER_ACTIVITY");
        // 활동은 발생마다 별도 행 → aggregate_id를 eventId로 둬 UNIQUE(aggregate_type,aggregate_id,event_type) 충돌 회피.
        assertThat(saved.getAggregateId()).isEqualTo(saved.getEventId().toString());
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);

        JsonNode env = objectMapper.readTree(saved.getPayload());
        assertThat(env.get("eventType").asText()).isEqualTo("user.activity.occurred");
        assertThat(env.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(env.get("producer").asText()).isEqualTo("inventory-service");
        assertThat(env.get("eventId").asText()).isEqualTo(saved.getEventId().toString());
        assertThat(env.get("correlationId").asText()).isEqualTo("INV-" + saved.getEventId());
        assertThat(env.get("occurredAt").asText()).isEqualTo(env.path("payload").path("occurredAt").asText());

        JsonNode payload = env.get("payload");
        assertThat(payload.get("employeeNo").asText()).isEqualTo("ADMIN002");
        assertThat(payload.get("action").asText()).isEqualTo("STOCK_ADJUSTED");
        assertThat(payload.get("title").asText()).isEqualTo("엔진오일 필터");
        assertThat(payload.get("content").asText()).isEqualTo("HMC-EN-00214");
        assertThat(payload.get("status").asText()).isEqualTo("-3");
    }

    @Test
    void record는_인증컨텍스트가_없으면_employeeNo를_SYSTEM으로_status_null로_적재한다() throws Exception {
        OutboxUserActivityRecorder recorder =
                new OutboxUserActivityRecorder(outboxDao, Optional::empty);

        recorder.record(UserActivityAction.WAREHOUSE_CREATED, "서울 1창고", "WH-SE-001", null);

        OutboxEvent saved = outboxDao.findAll().get(0);
        JsonNode payload = objectMapper.readTree(saved.getPayload()).get("payload");
        assertThat(payload.get("employeeNo").asText()).isEqualTo("SYSTEM");
        assertThat(payload.get("title").asText()).isEqualTo("서울 1창고");
        assertThat(payload.get("content").asText()).isEqualTo("WH-SE-001");
        assertThat(payload.get("status").isNull()).isTrue();
    }
}
