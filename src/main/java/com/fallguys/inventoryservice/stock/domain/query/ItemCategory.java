package com.fallguys.inventoryservice.stock.domain.query;

/**
 * Item 마스터에서 조회한 부품 분류(대분류·중분류) 값 객체. 상세 패널 표시 전용이며 stock에는 저장하지 않는다.
 * Item 서비스 연동 비활성/실패 시 두 값 모두 null로 강등될 수 있다.
 */
public record ItemCategory(
        String majorCategory,
        String middleCategory
) {
}
