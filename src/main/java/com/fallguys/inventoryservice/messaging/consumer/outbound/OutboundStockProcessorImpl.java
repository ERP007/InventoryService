package com.fallguys.inventoryservice.messaging.consumer.outbound;

import com.fallguys.inventoryservice.messaging.consumer.StockResultEventFactory;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.messaging.event.EventEnvelope;
import com.fallguys.inventoryservice.messaging.inbox.InboxEvent;
import com.fallguys.inventoryservice.messaging.inbox.InboxEventJpaDao;
import com.fallguys.inventoryservice.messaging.outbox.OutboxEventJpaDao;
import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.stock.domain.StockOutboundService;
import com.fallguys.inventoryservice.stock.domain.command.OutboundCommand;
import com.fallguys.inventoryservice.stock.domain.query.OutboundResult;

/**
 * 출고 명령 처리 트랜잭션 구현. applySuccess는 StockOutboundService.outbound()(REQUIRED)를 이 트랜잭션에 합류시켜
 * 차감과 applied outbox 적재를 원자적으로 커밋한다(dual-write 회피). 비즈니스 실패 시 outbound()의 도메인 예외가
 * 올라와 TX1이 롤백되고, consumer가 그것을 받아 recordRejection(TX-B)를 별도로 커밋한다.
 */
@Component
public class OutboundStockProcessorImpl implements OutboundStockProcessor {

    private static final String AGGREGATE_TYPE = "STOCK_OUTBOUND";

    private final StockOutboundService stockOutboundService;
    private final OutboxEventJpaDao outboxDao;
    private final InboxEventJpaDao inboxDao;
    private final StockResultEventFactory eventFactory;

    public OutboundStockProcessorImpl(StockOutboundService stockOutboundService,
                                      OutboxEventJpaDao outboxDao,
                                      InboxEventJpaDao inboxDao,
                                      StockResultEventFactory eventFactory) {
        this.stockOutboundService = stockOutboundService;
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
    public void applySuccess(EventEnvelope envelope, OutboundCommand command) {
        OutboundResult result = stockOutboundService.outbound(command); // REQUIRED → 이 트랜잭션에 합류
        outboxDao.save(eventFactory.applied(envelope.eventId(), result));
        inboxDao.save(processedInbox(envelope, command.sourceRef()));
    }

    @Override
    @Transactional
    public void recordRejection(EventEnvelope envelope, OutboundCommand command, BusinessException exception) {
        outboxDao.save(eventFactory.rejected(envelope.eventId(), command, exception));
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
