package com.fallguys.inventoryservice.stock.domain.query;

import java.time.LocalDate;

import com.fallguys.inventoryservice.stock.domain.MovementType;

/**
 * 특정 일자(KST)·이동 유형의 건수. 활동 차트 집계의 중간 결과로, 어댑터가 일자 변환·그룹해 반환하고
 * 서비스가 입고/출고/조정 카테고리로 접어 7일 달력에 채운다(빈 날은 이 목록에 없음).
 */
public record DailyMovementCount(LocalDate date, MovementType type, long count) {
}
