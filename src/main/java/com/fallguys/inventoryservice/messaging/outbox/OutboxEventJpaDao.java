package com.fallguys.inventoryservice.messaging.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * outbox 영속 인터페이스(Spring Data). relay 폴러와 결과 발행에서 사용한다(Slice 2/3).
 */
public interface OutboxEventJpaDao extends JpaRepository<OutboxEvent, Long> {

    /** relay 폴러용: 발행 대기(PENDING) 행을 created_at 오름차순으로 조회한다(배치 크기는 Pageable로 제한). */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    /**
     * 발행 확정된 행을 PUBLISHED로 전환한다(publisher confirm ack 이후). PENDING인 행만 전환해 멱등하다(이미 PUBLISHED면 0행).
     * 같은 트랜잭션의 1차 캐시 staleness를 막기 위해 clearAutomatically를 켠다.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            update OutboxEvent o
               set o.status = com.fallguys.inventoryservice.messaging.outbox.OutboxStatus.PUBLISHED,
                   o.publishedAt = :publishedAt
             where o.id = :id
               and o.status = com.fallguys.inventoryservice.messaging.outbox.OutboxStatus.PENDING
            """)
    int markPublished(@Param("id") Long id, @Param("publishedAt") Instant publishedAt);

    /**
     * 보존 기간이 지난 발행 완료(PUBLISHED) outbox 행을 일괄 삭제한다(하우스키핑). 반환값은 삭제된 행 수.
     *
     * <p>PENDING(미발행)은 제외하고 published_at이 cutoff 이전인 PUBLISHED 행만 지운다 — 발행 전 행을 지우면
     * 이벤트가 유실되므로, 브로커 수신이 확정된(PUBLISHED) 종료 상태 행만 정리한다(cutoff는 호출자가 보존 기간만큼 과거로 잡는다).
     * status·published_at에 인덱스가 없어 풀스캔이 될 수 있으므로 저빈도(1일 1회) 호출을 전제로 한다.
     * clearAutomatically로 1차 캐시를 비워 같은 트랜잭션의 후속 조회가 삭제 결과를 보게 한다.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            delete from OutboxEvent o
               where o.status = com.fallguys.inventoryservice.messaging.outbox.OutboxStatus.PUBLISHED
                 and o.publishedAt < :cutoff
            """)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
