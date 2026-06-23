package com.fallguys.inventoryservice.messaging.consumer.outbound;

import com.fallguys.inventoryservice.messaging.consumer.MalformedEventException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.messaging.event.EventEnvelope;
import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.stock.domain.command.OutboundCommand;

class OutboundStockConsumerTest {

    private static final String EVENT_ID = "11111111-1111-1111-1111-111111111111";

    private EventEnvelope envelope() {
        return new EventEnvelope(EVENT_ID, "inventory.stock.outbound.requested", 1,
                "sales-service", "2026-06-22T12:30:00Z", "SO-1", null);
    }

    private OutboundCommandPayload validPayload() {
        return new OutboundCommandPayload("SO-1", "WH-HQ-001",
                new OutboundCommandPayload.Executor("E1", "강상민"),
                List.of(new OutboundCommandPayload.Line("SKU-A", 3, 1)));
    }

    @Test
    void 중복수신이면_dedup만_하고_반환한다() {
        FakeProcessor processor = new FakeProcessor();
        processor.alreadyProcessed = true;
        OutboundStockConsumer consumer = new OutboundStockConsumer(processor);

        consumer.consume(envelope(), validPayload());

        assertThat(processor.calls).containsExactly("dedup");
    }

    @Test
    void 성공이면_applySuccess만_호출한다() {
        FakeProcessor processor = new FakeProcessor();
        OutboundStockConsumer consumer = new OutboundStockConsumer(processor);

        consumer.consume(envelope(), validPayload());

        assertThat(processor.calls).containsExactly("dedup", "apply");
    }

    @Test
    void 비즈니스_실패는_recordRejection으로_보낸다() {
        FakeProcessor processor = new FakeProcessor();
        processor.applyFailure = new InsufficientStockTestException();
        OutboundStockConsumer consumer = new OutboundStockConsumer(processor);

        consumer.consume(envelope(), validPayload());

        assertThat(processor.calls).containsExactly("dedup", "apply", "reject:INSUFFICIENT_STOCK");
    }

    @Test
    void 기술_실패는_전파되어_재시도_DLQ로_간다() {
        FakeProcessor processor = new FakeProcessor();
        processor.applyFailure = new RuntimeException("db down");
        OutboundStockConsumer consumer = new OutboundStockConsumer(processor);

        assertThatThrownBy(() -> consumer.consume(envelope(), validPayload()))
                .isInstanceOf(RuntimeException.class);
        assertThat(processor.calls).containsExactly("dedup", "apply"); // 거절로 안 보냄
    }

    @Test
    void 형식오류_payload는_MalformedEventException으로_DLQ로_간다() {
        FakeProcessor processor = new FakeProcessor();
        OutboundStockConsumer consumer = new OutboundStockConsumer(processor);
        OutboundCommandPayload invalid = new OutboundCommandPayload("SO-1", "WH-HQ-001",
                new OutboundCommandPayload.Executor("E1", "강상민"), List.of()); // lines 비어있음

        assertThatThrownBy(() -> consumer.consume(envelope(), invalid))
                .isInstanceOf(MalformedEventException.class);
        assertThat(processor.calls).containsExactly("dedup"); // 검증 실패 → 처리 안 함
    }

    // ---- fakes ----

    private static final class FakeProcessor implements OutboundStockProcessor {
        private boolean alreadyProcessed;
        private RuntimeException applyFailure;
        private final List<String> calls = new ArrayList<>();

        @Override
        public boolean isAlreadyProcessed(UUID eventId) {
            calls.add("dedup");
            return alreadyProcessed;
        }

        @Override
        public void applySuccess(EventEnvelope envelope, OutboundCommand command) {
            calls.add("apply");
            if (applyFailure != null) {
                throw applyFailure;
            }
        }

        @Override
        public void recordRejection(EventEnvelope envelope, OutboundCommand command, BusinessException exception) {
            calls.add("reject:" + exception.getCode());
        }
    }

    private static final class InsufficientStockTestException extends BusinessException {
        InsufficientStockTestException() {
            super("INSUFFICIENT_STOCK", "재고가 부족합니다.");
        }
    }
}
