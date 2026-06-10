package com.fallguys.inventoryservice.stock.domain.query;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;

/**
 * Item 마스터에서 조회한 부품 정보. 상세 패널의 대분류·중분류 표시와, 입고 시 신규 재고행 생성(name·unit·safetyStock)에 쓰인다.
 * 통합 비활성/실패 시 조회 자체가 비어(Optional.empty)서 반환된다.
 */
public record ItemInfo(
        String itemName,
        ItemUnit itemUnit,
        String majorCategory,
        String middleCategory,
        int safetyStock
) {
}
