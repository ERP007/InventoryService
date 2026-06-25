package com.fallguys.inventoryservice.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.config.JpaAuditingConfig;

/**
 * 실제 dao(H2)로 outbox 정리 스케줄러를 검증한다. JpaAuditingConfig를 Import해 @CreatedDate(created_at)를 채운다.
 * published_at은 markPublished(id, instant)로 직접 주입해 보존 경계(cutoff)를 제어한다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class OutboxCleanupSchedulerTest {

    @Autowired
    private OutboxEventJpaDao outboxDao;

    private Long savePublished(Instant publishedAt) {
        Long id = outboxDao.saveAndFlush(newPending()).getId();
        outboxDao.markPublished(id, publishedAt);
        return id;
    }

    private Long savePending() {
        return outboxDao.saveAndFlush(newPending()).getId();
    }

    private OutboxEvent newPending() {
        String eventType = "inventory.stock.outbound.applied";
        return OutboxEvent.pending("STOCK_OUTBOUND", UUID.randomUUID().toString(), eventType, UUID.randomUUID(),
                "erp.events", eventType + ".sales", "{}");
    }

    @Test
    void async가_켜지면_보존기간_지난_PUBLISHED만_삭제하고_나머지는_보존한다() {
        Long oldPublished = savePublished(Instant.now().minus(4, ChronoUnit.DAYS));    // 삭제 대상
        Long recentPublished = savePublished(Instant.now().minus(1, ChronoUnit.DAYS)); // 보존(최근)
        Long pending = savePending();                                                  // 보존(미발행)

        OutboxCleanupScheduler scheduler = new OutboxCleanupScheduler(outboxDao, true, 3);
        scheduler.purgePublished();

        assertThat(outboxDao.findById(oldPublished)).isEmpty();
        assertThat(outboxDao.findById(recentPublished)).isPresent();
        assertThat(outboxDao.findById(pending)).isPresent();
    }

    @Test
    void async가_꺼져있으면_아무것도_삭제하지_않는다() {
        Long oldPublished = savePublished(Instant.now().minus(10, ChronoUnit.DAYS));

        OutboxCleanupScheduler scheduler = new OutboxCleanupScheduler(outboxDao, false, 3);
        scheduler.purgePublished();

        assertThat(outboxDao.findById(oldPublished)).isPresent();
    }
}
