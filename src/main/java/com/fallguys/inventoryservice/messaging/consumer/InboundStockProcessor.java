package com.fallguys.inventoryservice.messaging.consumer;

import java.util.UUID;

import com.fallguys.inventoryservice.messaging.event.EventEnvelope;
import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.stock.domain.command.InboundCommand;

/**
 * 입고 명령 처리의 트랜잭션 단위(2-TX 데모케이션). consumer(비-트랜잭션)가 이 빈을 호출해야 프록시 @Transactional이 적용된다.
 * source(SALES/PROCUREMENT)는 결과 이벤트 routing key를 가르기 위해 전달한다.
 */
public interface InboundStockProcessor {

    /** 이미 처리한 메시지인지(eventId inbox 존재) 확인한다. */
    boolean isAlreadyProcessed(UUID eventId);

    /** 성공 경로(TX1): 재고 증가 + applied outbox + inbox PROCESSED를 한 트랜잭션에서 커밋한다. */
    void applySuccess(EventEnvelope envelope, InboundCommand command, CommandSource source);

    /** 거절 경로(TX-B): 증가 롤백과 별개의 새 트랜잭션에서 rejected outbox + inbox PROCESSED만 커밋한다. */
    void recordRejection(EventEnvelope envelope, InboundCommand command, CommandSource source, BusinessException exception);
}
