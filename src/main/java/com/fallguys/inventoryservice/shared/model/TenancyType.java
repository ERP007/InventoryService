package com.fallguys.inventoryservice.shared.model;

/**
 * 사용자 소속 타입(JWT tenancy_type 클레임). 재고 등 조회 범위 분기에 사용한다.
 * ADMIN·HQ는 전사 범위, BRANCH는 자기 창고(tenancy_code)로 한정된다.
 */
public enum TenancyType {
    ADMIN,
    HQ,
    BRANCH
}
