package com.fallguys.inventoryservice.messaging.inbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * inbox 영속 인터페이스(Spring Data). 중복 수신 판정(existsById)과 상태 갱신에 사용한다(Slice 3).
 */
public interface InboxEventJpaDao extends JpaRepository<InboxEvent, UUID> {

    /**
     * 보존 기간이 지난 처리 완료(PROCESSED) inbox 행을 일괄 삭제한다(하우스키핑). 반환값은 삭제된 행 수.
     *
     * <p>RECEIVED(처리 중)·FAILED(조사 대상)는 제외하고 processed_at이 cutoff 이전인 PROCESSED 행만 지운다 —
     * 재전달 dedup 윈도우를 넘긴 행만 정리해 중복 수신 방어를 깨지 않기 위함이다(cutoff는 호출자가 보존 기간만큼 과거로 잡는다).
     * status·processed_at에 인덱스가 없어 풀스캔이 될 수 있으므로 저빈도(1일 1회) 호출을 전제로 한다.
     * clearAutomatically로 1차 캐시를 비워 같은 트랜잭션의 후속 조회가 삭제 결과를 보게 한다.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("""
            delete from InboxEvent i
               where i.status = com.fallguys.inventoryservice.messaging.inbox.InboxStatus.PROCESSED
                 and i.processedAt < :cutoff
            """)
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
