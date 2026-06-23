package com.fallguys.inventoryservice.messaging.outbox;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * outbox 영속 인터페이스(Spring Data). relay 폴러와 결과 발행에서 사용한다(Slice 2/3).
 */
public interface OutboxEventJpaDao extends JpaRepository<OutboxEvent, Long> {

    /** relay 폴러용: 발행 대기(PENDING) 행을 created_at 오름차순으로 조회한다(배치 크기는 Pageable로 제한). */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
