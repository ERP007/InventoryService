package com.fallguys.inventoryservice.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class Warehouse {

    private final Long id;
    private final String code;      // 비즈니스 식별자, 변경 불가
    private String name;
    private final WarehouseType type;
    private final Long branchId;    // HQ이면 null, DEALER이면 BranchLocation PK
    private String address;
    private boolean active;
    private final String createdBy;
    private String updatedBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;


    /**
     * 창고 정보를 수정한다. 코드·유형·소속 지점은 변경 불가.
     *
     * @param name      변경할 창고명
     * @param address   변경할 주소
     * @param updatedBy 수정자 사번
     */
    public void update(String name, String address, String updatedBy) {
        this.name = name;
        this.address = address;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    /**
     * 창고를 비활성화한다. 이미 비활성 상태여도 멱등으로 처리한다.
     *
     * @param updatedBy 비활성 처리자 사번
     */
    public void deactivate(String updatedBy) {
        this.active = false;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    // 이 창고가 특정 지점 소속인지 여부 — 테넌시 격리에 사용
    public boolean belongsToBranch(Long targetBranchId) {
        return targetBranchId != null && targetBranchId.equals(this.branchId);
    }

    public boolean isHq() {
        return this.type == WarehouseType.HQ;
    }


}
