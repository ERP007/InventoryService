package com.fallguys.inventoryservice.stock.domain;

import java.util.Optional;

import com.fallguys.inventoryservice.stock.domain.query.ItemInfo;

/**
 * [DIP] 부품 정보(이름·단위·분류·기본 안전재고)를 외부 Item 서비스에서 조회하는 추상화(의도 기반 이름).
 * 구현은 infrastructure.client가 담당하며, 기술적 호출 실패는 ItemServiceUnavailableException로 번역한다(§10).
 *
 * 반환: 있으면 {@code Optional.of(ItemInfo)}, 통합 비활성·Item에 부품 없음(404)이면 {@code Optional.empty()}.
 *
 * @throws com.fallguys.inventoryservice.stock.domain.exception.ItemServiceUnavailableException
 *         외부 호출이 기술적으로 실패할 때(연결 실패·타임아웃·5xx·단위 계약 불일치 등)
 */
public interface ItemInfoProvider {

    Optional<ItemInfo> findBySku(String sku);

    /**
     * Item 통합이 활성인지 여부. 비활성(Item 미배포)이면 {@link #findBySku}는 호출 없이 {@code empty}를 반환하므로,
     * 호출 측이 "비활성이라 검증 불가"와 "활성인데 부품 없음(404 대상)"을 구분해야 할 때 이 값으로 분기한다. 기본값 true.
     */
    default boolean isEnabled() {
        return true;
    }
}
