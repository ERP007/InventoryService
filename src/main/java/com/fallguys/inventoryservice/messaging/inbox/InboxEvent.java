package com.fallguys.inventoryservice.messaging.inbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수신 이벤트 기록(Inbox). consumer가 받은 메시지의 eventId를 PK로 적재해 중복 수신을 막는다(메시지 dedup).
 * 처음 받은 메시지는 RECEIVED로 저장 후 비즈니스 처리 → PROCESSED, 이미 있으면 재처리하지 않고 discard(ack)한다.
 * 업무(재고) 중복 반영은 stock_movement의 sourceRef×warehouse 멱등이 별도로 막는 2차 방어막이다.
 */
@Entity
@Table(name = "inbox_event")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "producer", nullable = false, length = 80)
    private String producer;

    @Column(name = "aggregate_type", length = 60)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 120)
    private String aggregateId;

    @Column(name = "payload", nullable = false, length = 10000)
    private String payload;

    @Column(name = "headers", length = 2000)
    private String headers;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @CreatedDate
    @Column(name = "received_at", updatable = false, nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    private InboxEvent(UUID eventId, String eventType, String producer, String aggregateType,
                       String aggregateId, String payload, String headers) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.producer = producer;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.headers = headers;
        this.status = InboxStatus.RECEIVED;
        this.retryCount = 0;
    }

    /** 처음 수신한 메시지를 RECEIVED로 적재한다. received_at은 Auditing이 채운다. */
    public static InboxEvent received(UUID eventId, String eventType, String producer, String aggregateType,
                                      String aggregateId, String payload, String headers) {
        return new InboxEvent(eventId, eventType, producer, aggregateType, aggregateId, payload, headers);
    }

    /** 비즈니스 처리 완료(RECEIVED→PROCESSED). */
    public void markProcessed(Instant processedAt) {
        this.status = InboxStatus.PROCESSED;
        this.processedAt = processedAt;
    }

    /** 기술 실패 기록(재시도 카운트 증가 + 마지막 오류). */
    public void markFailed(String lastError) {
        this.status = InboxStatus.FAILED;
        this.retryCount += 1;
        this.lastError = lastError;
    }
}
