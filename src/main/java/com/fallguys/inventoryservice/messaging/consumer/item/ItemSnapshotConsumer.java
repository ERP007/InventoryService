package com.fallguys.inventoryservice.messaging.consumer.item;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.messaging.event.EventEnvelope;
import com.fallguys.inventoryservice.messaging.inbox.InboxEvent;
import com.fallguys.inventoryservice.messaging.inbox.InboxEventJpaDao;
import com.fallguys.inventoryservice.stock.domain.StockItemSyncService;

/**
 * 품목 마스터 스냅샷 소비(fire-and-forget). 결과 이벤트가 없고 비즈니스 거절 개념도 없어 2-TX가 필요 없다 —
 * dedup → 스냅샷 3컬럼 반영 → inbox PROCESSED를 한 트랜잭션으로 커밋하면 끝(대상 stock 행 0건도 정상).
 * 기술 실패(DB 오류 등)는 예외 전파로 재시도/DLQ. 스냅샷 갱신은 절대값 last-write-wins라 본래 멱등이지만 inbox로도 중복을 막는다.
 *
 * <p>기존 동기 sync 메서드 3개를 같은 트랜잭션에서 재사용한다(REQUIRED로 합류). 단일 bulk UPDATE로 합치는 최적화는 추후.
 */
@Component
public class ItemSnapshotConsumer {

    private static final String AGGREGATE_TYPE = "ITEM";

    private final StockItemSyncService syncService;
    private final InboxEventJpaDao inboxDao;

    public ItemSnapshotConsumer(StockItemSyncService syncService, InboxEventJpaDao inboxDao) {
        this.syncService = syncService;
        this.inboxDao = inboxDao;
    }

    @Transactional
    public void consume(EventEnvelope envelope, ItemSnapshotPayload payload) {
        UUID eventId = UUID.fromString(envelope.eventId());
        if (inboxDao.existsById(eventId)) {
            return; // 중복 수신 — 이미 반영됨.
        }
        syncService.syncItemName(payload.sku(), payload.itemName());
        syncService.syncItemUnit(payload.sku(), payload.itemUnit());
        syncService.syncItemActive(payload.sku(), payload.active());
        inboxDao.save(processedInbox(envelope, payload.sku()));
    }

    private InboxEvent processedInbox(EventEnvelope envelope, String sku) {
        String payloadJson = envelope.payload() == null ? "{}" : envelope.payload().toString();
        InboxEvent inbox = InboxEvent.received(
                UUID.fromString(envelope.eventId()), envelope.eventType(), envelope.producer(),
                AGGREGATE_TYPE, sku, payloadJson, null);
        inbox.markProcessed(Instant.now());
        return inbox;
    }
}
