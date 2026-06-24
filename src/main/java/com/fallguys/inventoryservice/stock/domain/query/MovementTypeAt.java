package com.fallguys.inventoryservice.stock.domain.query;

import java.time.Instant;

import com.fallguys.inventoryservice.stock.domain.MovementType;

/**
 * 이동 발생시각·유형 투영(활동 차트 집계용 raw 행). 어댑터가 이 행들을 KST 일자로 변환·그룹해 일자별 건수를 만든다.
 */
public record MovementTypeAt(Instant performedAt, MovementType type) {
}
