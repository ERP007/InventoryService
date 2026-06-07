package com.fallguys.inventoryservice.stock.domain;

/**
 * 재고 이동 사유. 현재는 조정에서 사용하는 기본 3종이며 점진 확장한다(추후 입출고 사유 등 추가).
 * 응답에는 코드(name)를 노출하고 한글 라벨 매핑은 프론트가 담당한다.
 */
public enum MovementReason {
    DAMAGE,
    LOST,
    FOUND
}
