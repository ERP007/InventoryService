package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import java.time.Instant;

import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.Stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 JPA 엔티티. (sku × warehouse_id) 조합은 시스템 유일(UNIQUE).
 * 생성·수정자 사번(created_by/updated_by)과 시각은 Spring Data JPA Auditing이 채운다.
 */
@Entity
@Table(name = "stock",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_sku_warehouse", columnNames = {"sku", "warehouse_id"}))
@Check(name = "chk_stock_nonneg", constraints = "current_stock >= 0 AND safety_stock >= 0")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sku;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_unit", nullable = false)
    private ItemUnit itemUnit;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "current_stock", nullable = false)
    private int currentStock;

    @Column(name = "safety_stock", nullable = false)
    private int safetyStock;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    private StockEntity(String sku, String itemName, ItemUnit itemUnit, Long warehouseId, int currentStock, int safetyStock) {
        this.sku = sku;
        this.itemName = itemName;
        this.itemUnit = itemUnit;
        this.warehouseId = warehouseId;
        this.currentStock = currentStock;
        this.safetyStock = safetyStock;
    }

    /** 신규 재고 도메인을 영속 엔티티로 변환한다(id·감사 컬럼·version은 JPA/Auditing이 채운다). */
    public static StockEntity from(Stock stock) {
        return new StockEntity(stock.getSku(), stock.getItemName(), stock.getItemUnit(), stock.getWarehouseId(),
                stock.getQuantity(), stock.getSafetyStock());
    }

    /** 영속 엔티티를 도메인 모델로 변환한다(조회). */
    public Stock toDomain() {
        return Stock.of(id, sku, itemName, itemUnit, warehouseId, currentStock, safetyStock);
    }

    /** 조정 등으로 변동한 재고 수준(현재고·안전재고)을 도메인 상태로 동기화한다. id·sku·창고·감사 컬럼·version은 건드리지 않는다. */
    public void update(Stock stock) {
        this.currentStock = stock.getQuantity();
        this.safetyStock = stock.getSafetyStock();
    }

    /** 안전재고만 절대값으로 교체한다(현재고·식별·감사 컬럼은 건드리지 않음). 안전재고 조정 전용. */
    public void updateSafetyStock(int safetyStock) {
        this.safetyStock = safetyStock;
    }
}
