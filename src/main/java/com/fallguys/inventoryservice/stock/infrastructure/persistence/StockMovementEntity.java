package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import java.time.Instant;

import org.hibernate.annotations.Check;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.MovementReason;
import com.fallguys.inventoryservice.stock.domain.MovementType;
import com.fallguys.inventoryservice.stock.domain.StockMovement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 이동 이력 JPA 엔티티(append-only). 한 번 INSERT되면 UPDATE/DELETE하지 않으므로
 * 수정 시각·@Version은 두지 않고 발생 시각(performed_at)만 Auditing(@CreatedDate)으로 채운다.
 * 수행자 사번(executor_emp_no)·이름(executor_name), 부품명(item_name)·단위(item_unit)는 감사 자동값이 아니라
 * 도메인이 변동 당시를 박제한 스냅샷이라 명시 컬럼으로 둔다(Item/User 마스터와 실시간 일치는 보장하지 않는다).
 *
 * 제약:
 * - UNIQUE(source_ref, source_line_no, warehouse_id): PO/SO 문서 라인의 창고별 중복 적재 방지(멱등 보조).
 *   한 SO 라인이 본사 OUTBOUND·지점 INBOUND 2행을 만들어 (source_ref, source_line_no)를 공유하므로 warehouse_id까지 묶는다.
 *   조정은 source_ref·source_line_no가 NULL이라 제약되지 않는다.
 * - CHECK(delta <> 0 AND stock_after >= 0): 변화 없는 이력 금지·음수 재고 금지(도메인 불변식의 DB 백스톱).
 * 인덱스: (sku, performed_at)·(warehouse_id, performed_at)로 상세 이력·창고 필터·기간 조회를 가속한다.
 */
@Entity
@Table(name = "stock_movement",
        uniqueConstraints = @UniqueConstraint(name = "uk_movement_source",
                columnNames = {"source_ref", "source_line_no", "warehouse_id"}),
        indexes = {
                @Index(name = "idx_movement_sku_performed", columnList = "sku, performed_at"),
                @Index(name = "idx_movement_warehouse_performed", columnList = "warehouse_id, performed_at")
        })
@Check(name = "chk_movement_delta_stock", constraints = "delta <> 0 AND stock_after >= 0")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovementEntity {

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

    @Column(nullable = false)
    private int delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MovementType type;

    @Enumerated(EnumType.STRING)
    private MovementReason reason;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "source_line_no")
    private Integer sourceLineNo;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    @Column(name = "note")
    private String note;

    @Column(name = "executor_emp_no", nullable = false)
    private String executorEmpNo;

    @Column(name = "executor_name", nullable = false)
    private String executorName;

    @CreatedDate
    @Column(name = "performed_at", updatable = false, nullable = false)
    private Instant performedAt;

    private StockMovementEntity(String sku, String itemName, ItemUnit itemUnit, Long warehouseId, int delta,
                                MovementType type, MovementReason reason, String sourceRef, Integer sourceLineNo,
                                int stockAfter, String note, String executorEmpNo, String executorName) {
        this.sku = sku;
        this.itemName = itemName;
        this.itemUnit = itemUnit;
        this.warehouseId = warehouseId;
        this.delta = delta;
        this.type = type;
        this.reason = reason;
        this.sourceRef = sourceRef;
        this.sourceLineNo = sourceLineNo;
        this.stockAfter = stockAfter;
        this.note = note;
        this.executorEmpNo = executorEmpNo;
        this.executorName = executorName;
    }

    /** 신규 이동 이력 도메인을 영속 엔티티로 변환한다(id·performed_at은 IDENTITY·Auditing이 채운다). */
    public static StockMovementEntity from(StockMovement movement) {
        return new StockMovementEntity(movement.getSku(), movement.getItemName(), movement.getItemUnit(),
                movement.getWarehouseId(), movement.getDelta(), movement.getType(), movement.getReason(),
                movement.getSourceRef(), movement.getSourceLineNo(), movement.getStockAfter(), movement.getNote(),
                movement.getExecutorEmpNo(), movement.getExecutorName());
    }

    /** 영속 엔티티를 도메인 모델로 변환한다(조회). */
    public StockMovement toDomain() {
        return StockMovement.of(id, sku, itemName, itemUnit, warehouseId, delta, type, reason, sourceRef,
                sourceLineNo, stockAfter, note, executorEmpNo, executorName, performedAt);
    }
}
