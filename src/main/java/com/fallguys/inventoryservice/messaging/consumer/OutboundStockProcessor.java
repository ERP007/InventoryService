package com.fallguys.inventoryservice.messaging.consumer;

import java.util.UUID;

import com.fallguys.inventoryservice.messaging.event.EventEnvelope;
import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.stock.domain.command.OutboundCommand;

/**
 * 출고 명령 처리의 트랜잭션 단위(2-TX 데모케이션의 양쪽). consumer(비-트랜잭션)가 이 빈의 메서드를 호출해야
 * 프록시 기반 @Transactional이 적용된다(자기호출 회피). 그래서 분류·라우팅(consumer)과 트랜잭션(여기)을 빈으로 분리한다.
 */
public interface OutboundStockProcessor {

    /** 이미 처리한 메시지인지(eventId inbox 존재) 확인한다. 트랜잭션 밖 단순 조회. */
    boolean isAlreadyProcessed(UUID eventId);

    /**
     * 성공 경로(TX1): 재고 차감 + applied outbox + inbox PROCESSED를 한 트랜잭션에서 커밋한다.
     * 비즈니스 실패 시 도메인 예외가 올라오며 이 트랜잭션은 통째로 롤백된다(재고 미반영).
     */
    void applySuccess(EventEnvelope envelope, OutboundCommand command);

    /**
     * 거절 경로(TX-B): 차감 롤백과 별개의 새 트랜잭션에서 rejected outbox + inbox PROCESSED만 커밋한다.
     * (재고는 안 깎였지만 "거절했다"는 사실과 결과 이벤트는 영속 → 원 서비스가 PENDING을 FAILED로 전환.)
     */
    void recordRejection(EventEnvelope envelope, OutboundCommand command, BusinessException exception);
}
