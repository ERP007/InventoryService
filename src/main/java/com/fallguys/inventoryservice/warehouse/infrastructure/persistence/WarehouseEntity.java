package com.fallguys.inventoryservice.warehouse.infrastructure.persistence;

import java.time.Instant;

import com.fallguys.inventoryservice.warehouse.domain.Warehouse;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "warehouse")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WarehouseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WarehouseType type;

    @Column(name = "branch_id")
    private Long branchId;

    private String address;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    private WarehouseEntity(String code, String name, WarehouseType type,
                            Long branchId, String address, boolean active) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.branchId = branchId;
        this.address = address;
        this.active = active;
    }

    /** 신규 창고 도메인을 영속 엔티티로 변환한다(id·타임스탬프는 DB·@PrePersist가 채운다). */
    public static WarehouseEntity from(Warehouse warehouse) {
        return new WarehouseEntity(warehouse.getCode(), warehouse.getName(), warehouse.getType(),
                warehouse.getBranchId(), warehouse.getAddress(), warehouse.isActive());
    }

    /**
     * 변경 가능 항목을 수정한다(code·active·createdAt은 불변, version은 JPA @Version이 관리).
     * 유형↔branchId 정합은 도메인에서 이미 검증된 상태로 들어온다.
     */
    public void update(UpdateWarehouseCommand command) {
        this.name = command.name();
        this.type = command.type();
        this.branchId = command.branchId();
        this.address = command.address();
    }

    /** 최초 영속 시 생성·수정 시각을 현재 시각으로 채운다(도메인에 시계 의존을 두지 않기 위함). */
    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /** 수정 영속 시 수정 시각을 갱신한다. */
    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
