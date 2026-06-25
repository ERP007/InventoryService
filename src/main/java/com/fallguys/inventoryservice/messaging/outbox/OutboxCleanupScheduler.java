package com.fallguys.inventoryservice.messaging.outbox;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 하우스키핑 스케줄러. 매일 새벽 4시(KST 기본)에 보존 기간(기본 3일)이 지난 PUBLISHED outbox 행을 삭제해 테이블 누적을 막는다.
 *
 * <p>흐름: cutoff = 현재 시각 − retentionDays를 계산해 dao의 일괄 삭제에 넘기고, 삭제 행 수를 INFO로 남긴다.
 *
 * <p>게이트: inventory.messaging.async-enabled=false면 무동작이다(relay가 안 돌아 발행 행도 안 쌓인다).
 * PENDING(미발행)은 건드리지 않고 브로커 수신이 확정된 PUBLISHED만 정리한다 — 발행 전 행 삭제는 이벤트 유실이기 때문이다.
 * inbox 정리와 동일 주기·보존 기간을 기본값으로 쓰되 outbox-cleanup.* 로 독립 튜닝할 수 있다.
 *
 * <p>트랜잭션: 삭제 자체는 dao 메서드(@Transactional)가 담당한다. 외부 호출이 없어 롤백 경계는 그 한 번의 일괄 삭제뿐이다.
 */
@Component
public class OutboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupScheduler.class);

    private final OutboxEventJpaDao outboxDao;
    private final boolean asyncEnabled;
    private final int retentionDays;

    public OutboxCleanupScheduler(
            OutboxEventJpaDao outboxDao,
            @Value("${inventory.messaging.async-enabled:false}") boolean asyncEnabled,
            @Value("${inventory.messaging.outbox-cleanup.retention-days:3}") int retentionDays) {
        this.outboxDao = outboxDao;
        this.asyncEnabled = asyncEnabled;
        this.retentionDays = retentionDays;
    }

    /** 보존 기간(retentionDays)이 지난 PUBLISHED outbox 행을 삭제한다. async 비활성이면 즉시 종료한다. */
    @Scheduled(cron = "${inventory.messaging.outbox-cleanup.cron:0 0 4 * * *}",
            zone = "${inventory.messaging.outbox-cleanup.zone:Asia/Seoul}")
    public void purgePublished() {
        if (!asyncEnabled) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = outboxDao.deletePublishedBefore(cutoff);
        log.info("Outbox cleanup: deleted {} PUBLISHED rows older than {}d (cutoff={})", deleted, retentionDays, cutoff);
    }
}
