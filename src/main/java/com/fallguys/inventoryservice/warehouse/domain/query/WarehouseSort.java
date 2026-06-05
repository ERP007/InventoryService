package com.fallguys.inventoryservice.warehouse.domain.query;

import com.fallguys.inventoryservice.shared.query.SortDirection;

/**
 * 검증을 통과한 단일 정렬 조건. Spring Data Sort 등 기술 표현으로의 변환은 infrastructure가 담당한다.
 *
 * @param field     정렬 속성
 * @param direction 정렬 방향
 */
public record WarehouseSort(WarehouseSortField field, SortDirection direction) {

    public WarehouseSort {
        if (field == null || direction == null) {
            throw new IllegalArgumentException("field, direction must not be null");
        }
    }

    /** 응답에 그대로 되돌려줄 Spring Data 네이티브 포맷("code,asc"). */
    public String toParam() {
        return field.property() + "," + direction.paramValue();
    }
}
