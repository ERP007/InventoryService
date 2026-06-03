package com.fallguys.inventoryservice.domain;

import java.util.List;

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
}
