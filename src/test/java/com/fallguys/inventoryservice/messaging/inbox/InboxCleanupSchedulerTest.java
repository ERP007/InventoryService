package com.fallguys.inventoryservice.messaging.inbox;

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
 * 실제 dao(H2)로 inbox 정리 스케줄러를 검증한다. JpaAuditingConfig를 Import해 @CreatedDate(received_at)를 채운다.
 * processed_at은 markProcessed로 직접 주입해 보존 경계(cutoff)를 제어한다.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class InboxCleanupSchedulerTest {

    @Autowired
    private InboxEventJpaDao inboxDao;

    private UUID saveProcessed(Instant processedAt) {
        InboxEvent e = newReceived();
        e.markProcessed(processedAt);
        return inboxDao.saveAndFlush(e).getEventId();
    }

    private UUID saveReceived() {
        return inboxDao.saveAndFlush(newReceived()).getEventId();
    }

    // 한때 처리됐다가 실패로 전이한 행: processed_at은 오래됐지만 status=FAILED라 삭제 대상이 아님(status 필터 검증용).
    private UUID saveFailed(Instant processedAt) {
        InboxEvent e = newReceived();
        e.markProcessed(processedAt);
        e.markFailed("boom");
        return inboxDao.saveAndFlush(e).getEventId();
    }

    private InboxEvent newReceived() {
        return InboxEvent.received(UUID.randomUUID(), "inventory.stock.outbound.applied",
                "sales-service", "STOCK_OUTBOUND", "SO-1", "{}", null);
    }

    @Test
    void async가_켜지면_보존기간_지난_PROCESSED만_삭제하고_나머지는_보존한다() {
        UUID oldProcessed = saveProcessed(Instant.now().minus(4, ChronoUnit.DAYS));    // 삭제 대상
        UUID recentProcessed = saveProcessed(Instant.now().minus(1, ChronoUnit.DAYS)); // 보존(최근)
        UUID received = saveReceived();                                                // 보존(처리 중)
        UUID failedOld = saveFailed(Instant.now().minus(5, ChronoUnit.DAYS));          // 보존(FAILED)

        InboxCleanupScheduler scheduler = new InboxCleanupScheduler(inboxDao, true, 3);
        scheduler.purgeProcessed();

        assertThat(inboxDao.findById(oldProcessed)).isEmpty();
        assertThat(inboxDao.findById(recentProcessed)).isPresent();
        assertThat(inboxDao.findById(received)).isPresent();
        assertThat(inboxDao.findById(failedOld)).isPresent();
    }

    @Test
    void async가_꺼져있으면_아무것도_삭제하지_않는다() {
        UUID oldProcessed = saveProcessed(Instant.now().minus(10, ChronoUnit.DAYS));

        InboxCleanupScheduler scheduler = new InboxCleanupScheduler(inboxDao, false, 3);
        scheduler.purgeProcessed();

        assertThat(inboxDao.findById(oldProcessed)).isPresent();
    }
}
