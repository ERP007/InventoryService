package com.fallguys.inventoryservice.messaging.inbox;

/** inbox 처리 상태. RECEIVED(수신) → PROCESSED(처리 완료) / FAILED(기술 실패 기록). */
public enum InboxStatus {
    RECEIVED,
    PROCESSED,
    FAILED
}
