package com.fallguys.inventoryservice.warehouse.domain;

import com.fallguys.inventoryservice.warehouse.domain.command.CreateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseBranchRuleException;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;

import lombok.Getter;

/**
 * 창고 애그리거트 루트. 식별자(id)로 동등성이 결정되며 JPA에 의존하지 않는다.
 * 생성 시점의 불변식(유형↔소속지점 정합, active=true)을 도메인이 보장한다.
 */
@Getter
public class Warehouse {

    private final Long id;
    private final String code;
    private final String name;
    private final WarehouseType type;
    private final Long branchId;
    private final String address;
    private final boolean active;

    private Warehouse(Long id, String code, String name, WarehouseType type,
                      Long branchId, String address, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.branchId = branchId;
        this.address = address;
        this.active = active;
    }

    /**
     * 신규 창고를 생성한다. id는 영속 시 발급되며 active는 항상 true다.
     * 불변식: DEALER는 소속 지점(branchId) 필수, HQ는 소속 지점을 가질 수 없다.
     *
     * @throws WarehouseBranchRuleException 유형↔branchId 정합 위반 시(400)
     */
    public static Warehouse create(CreateWarehouseCommand command) {
        // HQ / DEALER 와 소속 지점 관계에 대한 validation
        validateBranchRule(command.type(), command.branchId());

        return new Warehouse(null, command.code(), command.name(), command.type(),
                command.branchId(), command.address(), true);
    }

    /**
     * 유형↔소속지점(branchId) 정합 불변식. 생성·수정 모두에서 사용한다(같은 패키지의 서비스가 재사용).
     * DEALER는 branchId 필수, HQ는 branchId 불가.
     *
     * @throws WarehouseBranchRuleException 정합 위반 시(400)
     */
    static void validateBranchRule(WarehouseType type, Long branchId) {
        if (type == WarehouseType.DEALER && branchId == null) {
            throw new WarehouseBranchRuleException("DEALER 유형은 소속 지점(branchId)이 필수입니다.");
        }
        if (type == WarehouseType.HQ && branchId != null) {
            throw new WarehouseBranchRuleException("HQ 유형은 소속 지점(branchId)을 가질 수 없습니다.");
        }
    }
}
