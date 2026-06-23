package com.fallguys.inventoryservice.messaging.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.config.JpaAuditingConfig;
import com.fallguys.inventoryservice.messaging.outbox.OutboxEvent;
import com.fallguys.inventoryservice.messaging.outbox.OutboxEventJpaDao;
import com.fallguys.inventoryservice.messaging.outbox.OutboxStatus;

/**
 * 실제 dao(H2) + fake MessagePublisher로 relay 폴러를 검증한다. JpaAuditingConfig를 Import해 @CreatedDate(created_at)를 채운다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class OutboxRelayTest {

    @Autowired
    private OutboxEventJpaDao outboxDao;

    private OutboxEvent pending(String sourceRef) {
        String eventType = "inventory.stock.outbound.applied";
        return OutboxEvent.pending("STOCK_OUTBOUND", sourceRef, eventType, UUID.randomUUID(),
                "erp.events", eventType + ".sales", "{\"sourceRef\":\"" + sourceRef + "\"}");
    }

    @Test
    void async가_꺼져있으면_아무것도_발행하지_않고_PENDING으로_둔다() {
        outboxDao.save(pending("SO-1"));
        RecordingPublisher publisher = new RecordingPublisher(true);
        OutboxRelay relay = new OutboxRelay(outboxDao, publisher, false, 100);

        relay.publishPending();

        assertThat(publisher.calls).isEmpty();
        assertThat(outboxDao.findAll()).allMatch(e -> e.getStatus() == OutboxStatus.PENDING);
    }

    @Test
    void confirm_ack면_PUBLISHED로_전환하고_publishedAt을_채운다() {
        Long id = outboxDao.save(pending("SO-1")).getId();
        RecordingPublisher publisher = new RecordingPublisher(true);
        OutboxRelay relay = new OutboxRelay(outboxDao, publisher, true, 100);

        relay.publishPending();

        assertThat(publisher.calls).hasSize(1);
        OutboxEvent reloaded = outboxDao.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(reloaded.getPublishedAt()).isNotNull();
    }

    @Test
    void confirm_nack면_PENDING으로_남겨_다음_폴에서_재시도한다() {
        Long id = outboxDao.save(pending("SO-2")).getId();
        RecordingPublisher publisher = new RecordingPublisher(false); // nack/타임아웃
        OutboxRelay relay = new OutboxRelay(outboxDao, publisher, true, 100);

        relay.publishPending();

        assertThat(publisher.calls).hasSize(1);
        OutboxEvent reloaded = outboxDao.findById(id).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getPublishedAt()).isNull();
    }

    @Test
    void 한_행_발행이_예외여도_나머지_행은_계속_처리한다() {
        Long ok1 = outboxDao.save(pending("SO-A")).getId();
        Long boom = outboxDao.save(pending("SO-B")).getId();
        Long ok2 = outboxDao.save(pending("SO-C")).getId();
        RecordingPublisher publisher = new RecordingPublisher(true);
        publisher.throwForSourceRef = "SO-B";
        OutboxRelay relay = new OutboxRelay(outboxDao, publisher, true, 100);

        relay.publishPending();

        assertThat(outboxDao.findById(ok1).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(outboxDao.findById(boom).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outboxDao.findById(ok2).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    // ---- fake ----

    private static final class RecordingPublisher implements MessagePublisher {
        private final boolean ack;
        private final List<String> calls = new ArrayList<>();
        private String throwForSourceRef; // payload에 이 sourceRef가 있으면 발행 예외

        RecordingPublisher(boolean ack) {
            this.ack = ack;
        }

        @Override
        public boolean publishConfirmed(String exchange, String routingKey, String eventId, String eventType, String payload) {
            calls.add(eventId);
            if (throwForSourceRef != null && payload.contains(throwForSourceRef)) {
                throw new RuntimeException("publish boom");
            }
            return ack;
        }
    }
}
