package com.fallguys.inventoryservice.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.messaging.event.EventEnvelope;
import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.stock.domain.command.InboundCommand;
import com.fallguys.inventoryservice.stock.domain.exception.ItemNotFoundException;
import com.fallguys.inventoryservice.stock.domain.exception.ItemServiceUnavailableException;

class InboundStockConsumerTest {

    private static final String EVENT_ID = "22222222-2222-2222-2222-222222222222";

    private EventEnvelope envelope() {
        return new EventEnvelope(EVENT_ID, "inventory.stock.inbound.requested", 1,
                "procurement-service", "2026-06-22T14:10:00Z", "PO-1", null);
    }

    private InboundCommandPayload validPayload() {
        return new InboundCommandPayload("PO-1", "HQ-SE-001",
                new InboundCommandPayload.Executor("E1", "Smoke"),
                List.of(new InboundCommandPayload.Line("HMC-EN-00214", 20, 1)));
    }

    @Test
    void 중복수신이면_dedup만_하고_반환한다() {
        FakeProcessor processor = new FakeProcessor();
        processor.alreadyProcessed = true;
        new InboundStockConsumer(processor).consume(envelope(), validPayload(), CommandSource.PROCUREMENT);
        assertThat(processor.calls).containsExactly("dedup");
    }

    @Test
    void 성공이면_applySuccess만_호출하고_source를_전달한다() {
        FakeProcessor processor = new FakeProcessor();
        new InboundStockConsumer(processor).consume(envelope(), validPayload(), CommandSource.PROCUREMENT);
        assertThat(processor.calls).containsExactly("dedup", "apply:PROCUREMENT");
    }

    @Test
    void ITEM_NOT_FOUND는_비즈니스_거절로_recordRejection한다() {
        FakeProcessor processor = new FakeProcessor();
        processor.applyFailure = new ItemNotFoundException("HMC-EN-00214");
        new InboundStockConsumer(processor).consume(envelope(), validPayload(), CommandSource.SALES);
        assertThat(processor.calls).containsExactly("dedup", "apply:SALES", "reject:SALES:ITEM_NOT_FOUND");
    }

    @Test
    void ITEM_SERVICE_UNAVAILABLE는_기술실패로_전파되어_재시도_DLQ로_간다() {
        FakeProcessor processor = new FakeProcessor();
        processor.applyFailure = new ItemServiceUnavailableException("Item 서비스 장애", new RuntimeException());
        InboundStockConsumer consumer = new InboundStockConsumer(processor);

        assertThatThrownBy(() -> consumer.consume(envelope(), validPayload(), CommandSource.PROCUREMENT))
                .isInstanceOf(ItemServiceUnavailableException.class);
        assertThat(processor.calls).containsExactly("dedup", "apply:PROCUREMENT"); // 거절로 안 보냄
    }

    @Test
    void 형식오류_payload는_MalformedEventException으로_DLQ로_간다() {
        FakeProcessor processor = new FakeProcessor();
        InboundStockConsumer consumer = new InboundStockConsumer(processor);
        InboundCommandPayload invalid = new InboundCommandPayload("PO-1", "HQ-SE-001",
                new InboundCommandPayload.Executor("E1", "Smoke"), List.of()); // lines 비어있음

        assertThatThrownBy(() -> consumer.consume(envelope(), invalid, CommandSource.PROCUREMENT))
                .isInstanceOf(MalformedEventException.class);
        assertThat(processor.calls).containsExactly("dedup");
    }

    // ---- fakes ----

    private static final class FakeProcessor implements InboundStockProcessor {
        private boolean alreadyProcessed;
        private RuntimeException applyFailure;
        private final List<String> calls = new ArrayList<>();

        @Override
        public boolean isAlreadyProcessed(UUID eventId) {
            calls.add("dedup");
            return alreadyProcessed;
        }

        @Override
        public void applySuccess(EventEnvelope envelope, InboundCommand command, CommandSource source) {
            calls.add("apply:" + source);
            if (applyFailure != null) {
                throw applyFailure;
            }
        }

        @Override
        public void recordRejection(EventEnvelope envelope, InboundCommand command, CommandSource source, BusinessException exception) {
            calls.add("reject:" + source + ":" + exception.getCode());
        }
    }
}
