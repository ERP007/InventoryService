package com.fallguys.inventoryservice.messaging.consumer;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fallguys.inventoryservice.messaging.event.EventEnvelope;
import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.stock.domain.command.OutboundCommand;

/**
 * 출고 명령 소비 오케스트레이션(트랜잭션·RabbitMQ와 분리되어 단위 테스트 가능). 처리 분기:
 * <ol>
 *   <li>중복 수신(eventId inbox 존재) → 아무 것도 하지 않고 정상 반환 → 리스너가 ack(discard).</li>
 *   <li>형식 오류(payload 계약 위반) → MalformedEventException 전파 → 재시도 무의미, DLQ.</li>
 *   <li>비즈니스 실패(BusinessException) → recordRejection(TX-B) → 정상 반환 → ack.</li>
 *   <li>기술 실패(그 외 예외) → 전파 → 컨테이너 재시도 후 DLQ.</li>
 *   <li>성공 → applySuccess(TX1) → 정상 반환 → ack.</li>
 * </ol>
 * 예외 "분류기"는 곧 catch (BusinessException) 한 줄이다 — 모든 업무 거절이 BusinessException을 상속하므로.
 */
@Component
public class OutboundStockConsumer {

    private final OutboundStockProcessor processor;

    public OutboundStockConsumer(OutboundStockProcessor processor) {
        this.processor = processor;
    }

    public void consume(EventEnvelope envelope, OutboundCommandPayload payload) {
        UUID eventId = UUID.fromString(envelope.eventId());
        if (processor.isAlreadyProcessed(eventId)) {
            return; // 중복 수신 — 원 결과는 이미 outbox에 있고 relay가 전달한다.
        }
        payload.validate(); // 형식 오류 → MalformedEventException → DLQ
        OutboundCommand command = payload.toCommand();
        try {
            processor.applySuccess(envelope, command);
        } catch (BusinessException businessFailure) {
            // TX1 롤백(재고 미반영) 후, 거절 결과만 별도 트랜잭션으로 커밋한다.
            processor.recordRejection(envelope, command, businessFailure);
        }
    }
}
