package com.fallguys.inventoryservice.domain;

import java.util.List;
import java.util.Optional;

import com.fallguys.inventoryservice.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

/**
 * [DIP] 창고 영속성 추상화. 도메인이 정의하고 infrastructure가 구현한다.
 */
public interface WarehouseRepository {

    /**
     * 조회 조건에 맞는 창고 목록을 정렬하여 반환한다. 매칭이 없으면 빈 리스트.
     */
    List<WarehouseSummary> search(WarehouseSearchQuery query);

    /** 창고 코드 존재 여부. 등록 전 중복 검사에 사용한다. */
    boolean existsByCode(String code);

    /** 신규 창고를 저장하고 발급된 식별자를 반환한다. */
    Long save(Warehouse warehouse);

    /** 식별자로 창고 읽기 모델(소속 지점명 포함)을 조회한다. 없으면 empty. */
    Optional<WarehouseSummary> findSummaryById(Long id);
}
