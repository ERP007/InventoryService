package com.fallguys.inventoryservice.messaging.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 토폴로지 선언. inventory.messaging.async-enabled=true일 때만 실제로 선언한다(꺼져 있으면 빈 Declarables).
 *
 * <p>inventory가 "소비"하는 큐(+공용 exchange, DLQ)만 선언한다. 결과 이벤트(applied/rejected)는 erp.events에
 * routing key로 "발행만" 하므로 결과 큐(sales/procurement 소유)는 여기서 선언하지 않는다.
 * 각 소비 큐는 실패 메시지를 erp.dlx(DLX)로 dead-letter하며 "&lt;큐&gt;.dlq"가 이를 받는다.
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String COMMANDS_EXCHANGE = "erp.commands";
    public static final String EVENTS_EXCHANGE = "erp.events";
    public static final String DLX_EXCHANGE = "erp.dlx";

    public static final String INBOUND_PROCUREMENT_QUEUE = "inventory.stock-inbound-procurement.q";
    public static final String INBOUND_SALES_QUEUE = "inventory.stock-inbound-sales.q";
    public static final String OUTBOUND_SALES_QUEUE = "inventory.stock-outbound-sales.q";
    public static final String ITEM_SNAPSHOT_QUEUE = "inventory.item-master-snapshot.q";

    public static final String INBOUND_PROCUREMENT_RK = "inventory.stock.inbound.requested.procurement";
    public static final String INBOUND_SALES_RK = "inventory.stock.inbound.requested.sales";
    public static final String OUTBOUND_SALES_RK = "inventory.stock.outbound.requested.sales";
    public static final String ITEM_SNAPSHOT_RK = "item.master.snapshot.changed";

    /**
     * 토폴로지(exchange·소비 큐·DLQ·바인딩). async-enabled=false면 빈 선언을 반환해 아무 것도 만들지 않는다.
     * RabbitAdmin은 브로커 연결이 처음 열릴 때 이 선언들을 적용한다(기동 시 강제 연결 없음).
     */
    @Bean
    public Declarables erpMessagingTopology(
            @Value("${inventory.messaging.async-enabled:false}") boolean asyncEnabled) {
        if (!asyncEnabled) {
            return new Declarables();
        }

        TopicExchange commands = new TopicExchange(COMMANDS_EXCHANGE, true, false);
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(DLX_EXCHANGE, true, false);

        Queue inboundProcurement = consumerQueue(INBOUND_PROCUREMENT_QUEUE);
        Queue inboundSales = consumerQueue(INBOUND_SALES_QUEUE);
        Queue outboundSales = consumerQueue(OUTBOUND_SALES_QUEUE);
        Queue itemSnapshot = consumerQueue(ITEM_SNAPSHOT_QUEUE);

        Queue inboundProcurementDlq = new Queue(dlqName(INBOUND_PROCUREMENT_QUEUE), true);
        Queue inboundSalesDlq = new Queue(dlqName(INBOUND_SALES_QUEUE), true);
        Queue outboundSalesDlq = new Queue(dlqName(OUTBOUND_SALES_QUEUE), true);
        Queue itemSnapshotDlq = new Queue(dlqName(ITEM_SNAPSHOT_QUEUE), true);

        return new Declarables(
                commands, events, dlx,
                inboundProcurement, inboundSales, outboundSales, itemSnapshot,
                inboundProcurementDlq, inboundSalesDlq, outboundSalesDlq, itemSnapshotDlq,
                BindingBuilder.bind(inboundProcurement).to(commands).with(INBOUND_PROCUREMENT_RK),
                BindingBuilder.bind(inboundSales).to(commands).with(INBOUND_SALES_RK),
                BindingBuilder.bind(outboundSales).to(commands).with(OUTBOUND_SALES_RK),
                BindingBuilder.bind(itemSnapshot).to(events).with(ITEM_SNAPSHOT_RK),
                BindingBuilder.bind(inboundProcurementDlq).to(dlx).with(dlqName(INBOUND_PROCUREMENT_QUEUE)),
                BindingBuilder.bind(inboundSalesDlq).to(dlx).with(dlqName(INBOUND_SALES_QUEUE)),
                BindingBuilder.bind(outboundSalesDlq).to(dlx).with(dlqName(OUTBOUND_SALES_QUEUE)),
                BindingBuilder.bind(itemSnapshotDlq).to(dlx).with(dlqName(ITEM_SNAPSHOT_QUEUE)));
    }

    /** 메인 소비 큐: durable + 실패 메시지를 erp.dlx로 dead-letter(routing key = "&lt;큐&gt;.dlq"). */
    private static Queue consumerQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", dlqName(name))
                .build();
    }

    private static String dlqName(String queueName) {
        return queueName + ".dlq";
    }
}
