package com.fallguys.inventoryservice.stock.domain;

/**
 * 재고 조정 유형(유스케이스 입력). 변동량(delta) 계산 규칙을 각 유형이 보유한다.
 * - INCREASE: 입력 수량만큼 증가(+input)
 * - DECREASE: 입력 수량만큼 감소(-input)
 * - ADJUST(실사 보정): 입력 수량을 실측 잔량으로 보고 현재고와의 차이(input-current)를 변동량으로 한다
 */
public enum AdjustmentType {

    INCREASE {
        @Override
        public int delta(int currentQuantity, int inputQuantity) {
            return inputQuantity;
        }
    },
    DECREASE {
        @Override
        public int delta(int currentQuantity, int inputQuantity) {
            return -inputQuantity;
        }
    },
    ADJUST {
        @Override
        public int delta(int currentQuantity, int inputQuantity) {
            return inputQuantity - currentQuantity;
        }
    };

    /** 현재고와 입력 수량으로부터 변동량(delta)을 계산한다. */
    public abstract int delta(int currentQuantity, int inputQuantity);

    /** 대응하는 이동 유형으로 변환한다(이름 동일). */
    public MovementType toMovementType() {
        return MovementType.valueOf(name());
    }
}
