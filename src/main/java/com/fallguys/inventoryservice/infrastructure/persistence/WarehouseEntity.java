package com.fallguys.inventoryservice.infrastructure.persistence;

import java.time.Instant;

import com.fallguys.inventoryservice.domain.Warehouse;
import com.fallguys.inventoryservice.domain.model.WarehouseType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
}
