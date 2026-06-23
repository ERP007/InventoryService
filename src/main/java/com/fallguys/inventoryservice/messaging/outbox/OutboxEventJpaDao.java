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
}
