package com.fallguys.inventoryservice.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fallguys.inventoryservice.messaging.outbox.OutboxEvent;
import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.stock.domain.command.OutboundCommand;
import com.fallguys.inventoryservice.stock.domain.command.OutboundLine;
import com.fallguys.inventoryservice.stock.domain.query.OutboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.OutboundResult;
import com.fallguys.inventoryservice.stock.domain.command.InboundCommand;
import com.fallguys.inventoryservice.stock.domain.command.InboundLine;
import com.fallguys.inventoryservice.stock.domain.query.InboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.InboundResult;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseInactiveException;

class StockResultEventFactoryTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final StockResultEventFactory factory = new StockResultEventFactory();

    @Test
    void applied는_SUCCEEDED와_movements를_담고_라우팅을_채운다() throws Exception {
        OutboundResult result = new OutboundResult("SO-2026-001", "WH-HQ-001",
                List.of(new OutboundMovement(9L, "HMC-EN-00214", -3, 17)));

        OutboxEvent event = factory.applied(result);

        assertThat(event.getEventType()).isEqualTo("inventory.stock.outbound.applied");
        assertThat(event.getRoutingKey()).isEqualTo("inventory.stock.outbound.applied.sales");
        assertThat(event.getExchange()).isEqualTo("erp.events");
        assertThat(event.getAggregateType()).isEqualTo("STOCK_OUTBOUND");
        assertThat(event.getAggregateId()).isEqualTo("SO-2026-001");

        JsonNode payload = mapper.readTree(event.getPayload()).get("payload");
        assertThat(payload.get("status").asText()).isEqualTo("SUCCEEDED");
        JsonNode line = payload.get("movements").get(0);
        assertThat(line.get("sku").asText()).isEqualTo("HMC-EN-00214");
        assertThat(line.get("delta").asInt()).isEqualTo(-3);
        assertThat(line.get("quantity").asInt()).isEqualTo(3);  // |delta|
        assertThat(line.get("stockAfter").asInt()).isEqualTo(17);
    }

    @Test
    void rejected는_FAILED와_errorCode를_담고_retryable은_false다() throws Exception {
        OutboundCommand command = new OutboundCommand("SO-2026-001", "WH-HQ-001",
                List.of(new OutboundLine("HMC-EN-00214", 3, 1)), "E123", "강상민");

        OutboxEvent event = factory.rejected(command, new InsufficientStockTestException());

        assertThat(event.getEventType()).isEqualTo("inventory.stock.outbound.rejected");
        assertThat(event.getRoutingKey()).isEqualTo("inventory.stock.outbound.rejected.sales");
        assertThat(event.getAggregateId()).isEqualTo("SO-2026-001");

        JsonNode payload = mapper.readTree(event.getPayload()).get("payload");
        assertThat(payload.get("status").asText()).isEqualTo("FAILED");
        assertThat(payload.get("errorCode").asText()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(payload.get("errorMessage").asText()).isEqualTo("재고가 부족합니다.");
        assertThat(payload.get("retryable").asBoolean()).isFalse();
    }

    @Test
    void inboundApplied는_source_라우팅키와_SUCCEEDED를_담는다() throws Exception {
        InboundResult result = new InboundResult("PO-2026-001", "HQ-SE-001",
                List.of(new InboundMovement(7L, "HMC-EN-00214", 20, 120)));

        OutboxEvent event = factory.inboundApplied(result, CommandSource.PROCUREMENT);

        assertThat(event.getEventType()).isEqualTo("inventory.stock.inbound.applied");
        assertThat(event.getRoutingKey()).isEqualTo("inventory.stock.inbound.applied.procurement");
        assertThat(event.getAggregateType()).isEqualTo("STOCK_INBOUND");
        JsonNode payload = mapper.readTree(event.getPayload()).get("payload");
        assertThat(payload.get("status").asText()).isEqualTo("SUCCEEDED");
        JsonNode line = payload.get("movements").get(0);
        assertThat(line.get("delta").asInt()).isEqualTo(20);   // 입고는 양수
        assertThat(line.get("quantity").asInt()).isEqualTo(20);
        assertThat(line.get("stockAfter").asInt()).isEqualTo(120);
    }

    @Test
    void inboundRejected는_sales_라우팅키와_FAILED를_담는다() throws Exception {
        InboundCommand command = new InboundCommand("SO-2026-001", "WH-BR-001",
                List.of(new InboundLine("HMC-EN-00214", 3, 1)), "E1", "Smoke");

        OutboxEvent event = factory.inboundRejected(command, CommandSource.SALES, new WarehouseInactiveException("WH-BR-001"));

        assertThat(event.getEventType()).isEqualTo("inventory.stock.inbound.rejected");
        assertThat(event.getRoutingKey()).isEqualTo("inventory.stock.inbound.rejected.sales");
        JsonNode payload = mapper.readTree(event.getPayload()).get("payload");
        assertThat(payload.get("status").asText()).isEqualTo("FAILED");
        assertThat(payload.get("errorCode").asText()).isEqualTo("WAREHOUSE_INACTIVE");
        assertThat(payload.get("retryable").asBoolean()).isFalse();
    }

    private static final class InsufficientStockTestException extends BusinessException {
        InsufficientStockTestException() {
            super("INSUFFICIENT_STOCK", "재고가 부족합니다.");
        }
    }
}
