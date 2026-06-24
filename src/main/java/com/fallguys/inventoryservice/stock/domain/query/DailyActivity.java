package com.fallguys.inventoryservice.stock.domain.query;

import java.time.LocalDate;

/**
 * 일자별 활동 건수(입고/출고/조정). 조정은 INCREASE+DECREASE+ADJUST의 합이다.
 */
public record DailyActivity(LocalDate date, long inbound, long outbound, long adjust) {
}
