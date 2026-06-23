package com.fallguys.inventoryservice.messaging.consumer.inbound;

import com.fallguys.inventoryservice.messaging.consumer.CommandSource;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fallguys.inventoryservice.messaging.event.EventEnvelope;
import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.stock.domain.command.InboundCommand;

/**
 * 입고 명령 소비 오케스트레이션(SO 도착·PO 입고 공통, source로 구분). 분기는 출고와 동일:
 * 중복→discard / malformed→DLQ / BusinessException→recordRejection / 그 외→전파(재시도·DLQ) / 성공→applySuccess.
 *
 * <p>입고 특유: INSUFFICIENT_STOCK 없음(증가). ITEM_NOT_FOUND(business→reject)와 ITEM_SERVICE_UNAVAILABLE(technical→전파)이
 * catch(BusinessException) 한 줄로 자동 구분된다(ServiceUnavailableException은 BusinessException이 아니므로).
 */
@Component
public class InboundStockConsumer {

    private final InboundStockProcessor processor;

    public InboundStockConsumer(InboundStockProcessor processor) {
        this.processor = processor;
    }

    public void consume(EventEnvelope envelope, InboundCommandPayload payload, CommandSource source) {
        UUID eventId = UUID.fromString(envelope.eventId());
        if (processor.isAlreadyProcessed(eventId)) {
            return; // 중복 수신 — 원 결과는 이미 outbox에 있고 relay가 전달한다.
        }
        payload.validate(); // 형식 오류 → MalformedEventException → DLQ
        InboundCommand command = payload.toCommand();
        try {
            processor.applySuccess(envelope, command, source);
        } catch (BusinessException businessFailure) {
            // TX1 롤백(재고 미반영) 후, 거절 결과만 별도 트랜잭션으로 커밋한다.
            processor.recordRejection(envelope, command, source, businessFailure);
        }
    }
}
