package com.fallguys.inventoryservice.messaging.inbox;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Inbox 하우스키핑 스케줄러. 매일 새벽 4시(KST 기본)에 보존 기간(기본 3일)이 지난 PROCESSED inbox 행을 삭제해 테이블 누적을 막는다.
 *
 * <p>흐름: cutoff = 현재 시각 − retentionDays를 계산해 dao의 일괄 삭제에 넘기고, 삭제 행 수를 INFO로 남긴다.
 *
 * <p>게이트: inventory.messaging.async-enabled=false면 무동작이다(consumer가 안 돌아 inbox도 비어 있음).
 * RECEIVED(처리 중)·FAILED(조사 대상)는 건드리지 않고 재전달 dedup 윈도우를 넘긴 PROCESSED만 정리한다 —
 * 보존 기간은 브로커 재전달/DLQ 윈도우보다 길어야 안전하다(짧으면 재전달 시 dedup 누락→재처리 위험).
 *
 * <p>트랜잭션: 삭제 자체는 dao 메서드(@Transactional)가 담당한다. 외부 호출이 없어 롤백 경계는 그 한 번의 일괄 삭제뿐이다.
 */
@Component
public class InboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(InboxCleanupScheduler.class);

    private final InboxEventJpaDao inboxDao;
    private final boolean asyncEnabled;
    private final int retentionDays;

    public InboxCleanupScheduler(
            InboxEventJpaDao inboxDao,
            @Value("${inventory.messaging.async-enabled:false}") boolean asyncEnabled,
            @Value("${inventory.messaging.inbox-cleanup.retention-days:3}") int retentionDays) {
        this.inboxDao = inboxDao;
        this.asyncEnabled = asyncEnabled;
        this.retentionDays = retentionDays;
    }

    /** 보존 기간(retentionDays)이 지난 PROCESSED inbox 행을 삭제한다. async 비활성이면 즉시 종료한다. */
    @Scheduled(cron = "${inventory.messaging.inbox-cleanup.cron:0 0 4 * * *}",
            zone = "${inventory.messaging.inbox-cleanup.zone:Asia/Seoul}")
    public void purgeProcessed() {
        if (!asyncEnabled) {
            return;
        }
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = inboxDao.deleteProcessedBefore(cutoff);
        log.info("Inbox cleanup: deleted {} PROCESSED rows older than {}d (cutoff={})", deleted, retentionDays, cutoff);
    }
}
