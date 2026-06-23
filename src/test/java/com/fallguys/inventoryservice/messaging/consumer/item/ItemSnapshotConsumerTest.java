package com.fallguys.inventoryservice.messaging.consumer.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.config.JpaAuditingConfig;
import com.fallguys.inventoryservice.messaging.event.EventEnvelope;
import com.fallguys.inventoryservice.messaging.inbox.InboxEvent;
import com.fallguys.inventoryservice.messaging.inbox.InboxEventJpaDao;
import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockItemSyncService;
import com.fallguys.inventoryservice.stock.infrastructure.persistence.StockEntity;
import com.fallguys.inventoryservice.stock.infrastructure.persistence.StockRepositoryAdapter;

/**
 * 실제 dao(H2) + 실제 StockItemSyncService로 item 스냅샷 소비를 검증한다(2-TX 없는 단순 경로).
 */
@DataJpaTest
@Import({StockItemSyncService.class, StockRepositoryAdapter.class, JpaAuditingConfig.class, ItemSnapshotConsumer.class})
class ItemSnapshotConsumerTest {

    private static final String SKU = "HMC-EN-00214";

    @Autowired
    private ItemSnapshotConsumer consumer;

    @Autowired
    private TestEntityManager tem;

    @Autowired
    private InboxEventJpaDao inboxDao;

    @BeforeEach
    void seed() {
        tem.persist(StockEntity.from(Stock.create(SKU, "옛이름", ItemUnit.EA, 1L, 100, 50)));
        tem.flush();
    }

    private EventEnvelope envelope(String eventId) {
        return new EventEnvelope(eventId, "item.master.snapshot.changed", 1,
                "item-service", "2026-06-22T15:05:00Z", SKU, null);
    }

    private StockEntity findStock(String sku) {
        return tem.getEntityManager()
                .createQuery("select s from StockEntity s where s.sku = :sku", StockEntity.class)
                .setParameter("sku", sku)
                .getSingleResult();
    }

    @Test
    void 스냅샷이_이름_단위_활성을_갱신하고_inbox를_남긴다() {
        String eventId = "33333333-3333-3333-3333-333333333331";

        consumer.consume(envelope(eventId),
                new ItemSnapshotPayload(SKU, "새이름", ItemUnit.BOX, false, "2026-06-22T15:05:00Z"));
        tem.flush();   // 보류 중인 inbox INSERT를 DB로 내보낸 뒤
        tem.clear();   // 1차 캐시를 비워 재조회가 DB를 읽게 한다

        StockEntity stock = findStock(SKU);
        assertThat(stock.getItemName()).isEqualTo("새이름");
        assertThat(stock.getItemUnit()).isEqualTo(ItemUnit.BOX);
        assertThat(stock.isItemActive()).isFalse();
        assertThat(inboxDao.existsById(UUID.fromString(eventId))).isTrue();
    }

    @Test
    void 중복수신이면_갱신하지_않는다() {
        String eventId = "33333333-3333-3333-3333-333333333332";
        InboxEvent already = InboxEvent.received(UUID.fromString(eventId), "item.master.snapshot.changed",
                "item-service", "ITEM", SKU, "{}", null);
        already.markProcessed(Instant.now());
        inboxDao.saveAndFlush(already);

        consumer.consume(envelope(eventId),
                new ItemSnapshotPayload(SKU, "새이름", ItemUnit.BOX, false, null));
        tem.clear();

        assertThat(findStock(SKU).getItemName()).isEqualTo("옛이름"); // 중복이라 미반영
    }

    @Test
    void 대상_재고가_없어도_정상_처리하고_inbox를_남긴다() {
        String eventId = "33333333-3333-3333-3333-333333333333";

        consumer.consume(envelope(eventId),
                new ItemSnapshotPayload("NO-SUCH-SKU", "X", ItemUnit.EA, true, null));

        assertThat(inboxDao.existsById(UUID.fromString(eventId))).isTrue();
    }
}
