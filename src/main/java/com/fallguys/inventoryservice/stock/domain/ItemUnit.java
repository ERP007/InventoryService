package com.fallguys.inventoryservice.stock.domain;

/**
 * 재고 품목의 수량 단위. Item 마스터의 단위를 stock에 스냅샷으로 보관하기 위한 값이며, 문자열(enum name)로 영속화된다.
 * 기본 4종으로 시작하고 필요 시 확장한다(MovementReason과 동일한 확장 정책).
 */
public enum ItemUnit {
    EA,   // 낱개
    BOX,  // 박스
    SET,  // 세트
    L     // 리터(액체류)
}
