package com.fallguys.inventoryservice.messaging.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발행 대기 이벤트(Transactional Outbox). 비즈니스 변경과 같은 트랜잭션에서 INSERT되고, relay(폴러)가 PENDING을 읽어
 * RabbitMQ로 발행한다. Publisher Confirm ack(브로커 수신 확정)를 받은 뒤에만 PUBLISHED로 전환한다(유실 방지).
 * payload는 발행할 이벤트 envelope JSON 전체이며 relay가 거의 그대로 body로 보낸다.
 *
 * <p>멱등: UNIQUE(aggregate_type, aggregate_id, event_type)로 같은 비즈니스 결과의 재발행을 1행으로 막는다.
 * 예) 출고 결과 = (STOCK_OUTBOUND, sourceRef, inventory.stock.outbound.applied). 재처리 시 ON CONFLICT DO NOTHING.
 * 결과 그레인은 (방향 × sourceRef)이며 창고는 그 안에서 1개로 결정된다(필요 시 warehouse를 키에 추가).
 */
@Entity
@Table(name = "outbox_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_aggregate_event",
                columnNames = {"aggregate_type", "aggregate_id", "event_type"}),
        indexes = @Index(name = "idx_outbox_status_created", columnList = "status, created_at"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 60)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 120)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "exchange", nullable = false, length = 120)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 180)
    private String routingKey;

    // 발행할 envelope JSON. payload로 조회를 파고들지 않으므로 varchar로 둔다(필요 시 prod에서 text/jsonb로 승격).
    @Column(name = "payload", nullable = false, length = 10000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    private OutboxEvent(String aggregateType, String aggregateId, String eventType, UUID eventId,
                        String exchange, String routingKey, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventId = eventId;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    /** 발행 대기(PENDING) outbox 행을 만든다. created_at은 Auditing이 채운다. */
    public static OutboxEvent pending(String aggregateType, String aggregateId, String eventType, UUID eventId,
                                      String exchange, String routingKey, String payload) {
        return new OutboxEvent(aggregateType, aggregateId, eventType, eventId, exchange, routingKey, payload);
    }

    /** Publisher Confirm으로 브로커 수신이 확정된 뒤 호출한다(PENDING→PUBLISHED). */
    public void markPublished(Instant publishedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }
}
