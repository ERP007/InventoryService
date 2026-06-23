package com.fallguys.inventoryservice.messaging.relay;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fallguys.inventoryservice.messaging.outbox.OutboxEvent;
import com.fallguys.inventoryservice.messaging.outbox.OutboxEventJpaDao;
import com.fallguys.inventoryservice.messaging.outbox.OutboxStatus;

/**
 * Outbox relay 폴러. PENDING outbox 행을 주기적으로 읽어 브로커로 발행하고, publisher confirm ack를 받은 뒤에만
 * PUBLISHED로 전환한다(유실 방지). ack 못 받은 행(nack·타임아웃·발행 예외)은 PENDING으로 남아 다음 폴에서 재발행되며
 * (at-least-once), 중복은 consumer의 inbox(eventId)·sourceRef 멱등이 흡수한다.
 *
 * <p>게이트: inventory.messaging.async-enabled=false면 무동작(폴 즉시 종료). 발행과 상태 전환을 하나의 트랜잭션으로
 * 묶지 않는다 — 블로킹 confirm 대기 동안 DB 트랜잭션을 잡지 않기 위함이며, 상태 전환만 dao(@Transactional)에서 원자 처리한다.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventJpaDao outboxDao;
    private final MessagePublisher messagePublisher;
    private final boolean asyncEnabled;
    private final int batchSize;

    public OutboxRelay(
            OutboxEventJpaDao outboxDao,
            MessagePublisher messagePublisher,
            @Value("${inventory.messaging.async-enabled:false}") boolean asyncEnabled,
            @Value("${inventory.messaging.relay.batch-size:100}") int batchSize) {
        this.outboxDao = outboxDao;
        this.messagePublisher = messagePublisher;
        this.asyncEnabled = asyncEnabled;
        this.batchSize = batchSize;
    }

    /**
     * PENDING 배치를 발행한다. async 비활성이면 즉시 종료한다. 행별로 독립 처리하여 한 행의 실패가 배치 전체를 막지 않는다.
     * ack를 받은 행만 PUBLISHED로 전환하고, 그 외(nack·타임아웃·예외)는 PENDING으로 남겨 다음 폴에서 재시도한다.
     */
    @Scheduled(fixedDelayString = "${inventory.messaging.relay.poll-interval-ms:5000}")
    public void publishPending() {
        if (!asyncEnabled) {
            return;
        }
        List<OutboxEvent> pending = outboxDao.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, batchSize));
        for (OutboxEvent event : pending) {
            try {
                boolean acked = messagePublisher.publishConfirmed(
                        event.getExchange(), event.getRoutingKey(),
                        event.getEventId().toString(), event.getEventType(), event.getPayload());
                if (acked) {
                    outboxDao.markPublished(event.getId(), Instant.now());
                } else {
                    log.warn("Outbox publish not confirmed (nack/timeout), will retry: id={} eventType={}",
                            event.getId(), event.getEventType());
                }
            } catch (Exception e) {
                log.warn("Outbox publish failed, will retry: id={} eventType={}",
                        event.getId(), event.getEventType(), e);
            }
        }
    }
}
