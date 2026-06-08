package com.fallguys.inventoryservice.stock.domain;

/**
 * 재고 이동 유형(회계적 방향). append-only 이력에 저장하며 응답에는 코드(name)를 그대로 노출한다.
 * - INBOUND/OUTBOUND: PO/SO 입출고 흐름에서 생성(현재 미구현)
 * - INCREASE/DECREASE/ADJUST: 재고 조정에서 생성(INCREASE 증가, DECREASE 감소, ADJUST 실사 보정)
 */
public enum MovementType {
    INBOUND,
    OUTBOUND,
    INCREASE,
    DECREASE,
    ADJUST;

    /** 재고 조정으로 생성되는 유형인지 여부(INCREASE/DECREASE/ADJUST). */
    public boolean isAdjustment() {
        return this == INCREASE || this == DECREASE || this == ADJUST;
    }
}
