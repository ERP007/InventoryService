package com.fallguys.inventoryservice.messaging.consumer;

/**
 * 입고 명령의 출처. 결과 이벤트 routing key의 상대 도메인(suffix)을 결정한다(예: inventory.stock.inbound.applied.<b>sales</b>).
 * SO 도착 입고는 SALES, PO 입고는 PROCUREMENT. eventType 자체에는 상대 도메인을 붙이지 않는다(스키마 동일).
 */
public enum CommandSource {
    SALES("sales"),
    PROCUREMENT("procurement");

    private final String suffix;

    CommandSource(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }
}
