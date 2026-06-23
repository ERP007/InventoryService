package com.fallguys.inventoryservice.messaging.outbox;

/** outbox 발행 상태. PENDING(미발행) → PUBLISHED(publisher confirm으로 브로커 수신 확정). */
public enum OutboxStatus {
    PENDING,
    PUBLISHED
}
