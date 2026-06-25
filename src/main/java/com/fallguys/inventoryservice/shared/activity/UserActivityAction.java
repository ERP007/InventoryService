package com.fallguys.inventoryservice.shared.activity;

/**
 * 사용자 활동 로그 종류(user-service로 발행하는 user.activity.occurred 이벤트의 payload.action).
 * inventory가 책임지는 활동만 정의한다. consumer(user-service)가 이 문자열을 UserActionType으로 매핑해 배지로 표시한다.
 */
public enum UserActivityAction {
    WAREHOUSE_CREATED,        // 창고 추가
    WAREHOUSE_UPDATED,        // 창고 수정
    WAREHOUSE_STATUS_CHANGED, // 창고 상태 변경
    STOCK_ADJUSTED,           // 재고 조정
    SAFETY_STOCK_UPDATED      // 안전재고 조정
}
