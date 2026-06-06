package com.fallguys.inventoryservice.stock.domain;

import java.util.Optional;

import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;

/**
 * [DIP] 재고 영속성 추상화. 도메인이 정의하고 infrastructure가 구현한다.
 */
public interface StockRepository {

    /** (sku × warehouse) 조합의 재고 존재 여부. 신규 생성 전 중복 검사에 사용한다. */
    boolean existsBySkuAndWarehouseId(String sku, Long warehouseId);

    /** 신규 재고를 저장하고 발급된 식별자를 반환한다. */
    Long save(Stock stock);

    /** 저장 직후 식별자로 생성 결과(창고 코드 조인 포함)를 조회한다. 없으면 empty. */
    Optional<StockCreateResult> findResultById(Long id);
}
