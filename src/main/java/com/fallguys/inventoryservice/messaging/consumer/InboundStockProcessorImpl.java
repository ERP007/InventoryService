package com.fallguys.inventoryservice.messaging.consumer;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.messaging.event.EventEnvelope;
import com.fallguys.inventoryservice.messaging.inbox.InboxEvent;
import com.fallguys.inventoryservice.messaging.inbox.InboxEventJpaDao;
import com.fallguys.inventoryservice.messaging.outbox.OutboxEventJpaDao;
import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.stock.domain.StockInboundService;
import com.fallguys.inventoryservice.stock.domain.command.InboundCommand;
import com.fallguys.inventoryservice.stock.domain.query.InboundResult;

/**
 * 입고 명령 처리 트랜잭션 구현. applySuccess는 StockInboundService.inbound()(REQUIRED)를 이 트랜잭션에 합류시켜
 * 재고 증가와 applied outbox 적재를 원자적으로 커밋한다. 비즈니스 실패 시 도메인 예외가 올라와 TX1이 롤백되고,
 * consumer가 그것을 받아 recordRejection(TX-B)을 별도 커밋한다.
 *
 * <p>출고와 달리 신규 행 첫 입고 시 Item 서비스 호출이 트랜잭션 안에서 일어난다(StockInboundService 책임).
 * 그 호출의 기술 실패(ItemServiceUnavailableException)는 BusinessException이 아니므로 consumer에서 잡히지 않고 전파→재시도/DLQ.
 */
@Component
public class InboundStockProcessorImpl implements InboundStockProcessor {

    private static final String AGGREGATE_TYPE = "STOCK_INBOUND";

    private final StockInboundService stockInboundService;
    private final OutboxEventJpaDao outboxDao;
    private final InboxEventJpaDao inboxDao;
    private final StockResultEventFactory eventFactory;

    public InboundStockProcessorImpl(StockInboundService stockInboundService,
                                     OutboxEventJpaDao outboxDao,
                                     InboxEventJpaDao inboxDao,
                                     StockResultEventFactory eventFactory) {
        this.stockInboundService = stockInboundService;
        this.outboxDao = outboxDao;
        this.inboxDao = inboxDao;
        this.eventFactory = eventFactory;
    }

    @Override
    public boolean isAlreadyProcessed(UUID eventId) {
        return inboxDao.existsById(eventId);
    }

    @Override
    @Transactional
    public void applySuccess(EventEnvelope envelope, InboundCommand command, CommandSource source) {
        InboundResult result = stockInboundService.inbound(command); // REQUIRED → 이 트랜잭션에 합류
        outboxDao.save(eventFactory.inboundApplied(result, source));
        inboxDao.save(processedInbox(envelope, command.sourceRef()));
    }

    @Override
    @Transactional
    public void recordRejection(EventEnvelope envelope, InboundCommand command, CommandSource source, BusinessException exception) {
        outboxDao.save(eventFactory.inboundRejected(command, source, exception));
        inboxDao.save(processedInbox(envelope, command.sourceRef()));
    }

    private InboxEvent processedInbox(EventEnvelope envelope, String sourceRef) {
        String payloadJson = envelope.payload() == null ? "{}" : envelope.payload().toString();
        InboxEvent inbox = InboxEvent.received(
                UUID.fromString(envelope.eventId()), envelope.eventType(), envelope.producer(),
                AGGREGATE_TYPE, sourceRef, payloadJson, null);
        inbox.markProcessed(Instant.now());
        return inbox;
    }
}
