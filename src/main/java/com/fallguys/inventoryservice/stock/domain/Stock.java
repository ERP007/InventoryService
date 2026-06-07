package com.fallguys.inventoryservice.stock.domain;

import com.fallguys.inventoryservice.stock.domain.exception.InsufficientStockException;
import com.fallguys.inventoryservice.stock.domain.exception.NoStockChangeException;

import lombok.Getter;

/**
 * 재고 애그리거트 루트. (sku × warehouse) 조합으로 식별되는 단일 재고 상태를 표현하며 JPA에 의존하지 않는다.
 * 생성 시점의 불변식(수량·안전재고 ≥ 0, 식별자 필수)을 도메인이 보장한다.
 * 생성·수정자 사번 snapshot, 시각, version은 영속 계층(엔티티 + Auditing)이 관리한다.
 */
@Getter
public class Stock {

    private final Long id;
    private final String sku;
    private final String itemName;
    private final Long warehouseId;
    private int quantity;
    private final int safetyStock;

    private Stock(Long id, String sku, String itemName, Long warehouseId, int quantity, int safetyStock) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku는 필수입니다.");
        }
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("itemName은 필수입니다.");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId는 필수입니다.");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("수량(quantity)은 0 이상이어야 합니다.");
        }
        if (safetyStock < 0) {
            throw new IllegalArgumentException("안전재고(safetyStock)는 0 이상이어야 합니다.");
        }
        this.id = id;
        this.sku = sku;
        this.itemName = itemName;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.safetyStock = safetyStock;
    }

    /** 신규 재고를 생성한다. id는 영속 시 발급된다. */
    public static Stock create(String sku, String itemName, Long warehouseId, int quantity, int safetyStock) {
        return new Stock(null, sku, itemName, warehouseId, quantity, safetyStock);
    }

    /** 영속 엔티티에서 도메인 모델을 복원한다(조회용). */
    public static Stock of(Long id, String sku, String itemName, Long warehouseId, int quantity, int safetyStock) {
        return new Stock(id, sku, itemName, warehouseId, quantity, safetyStock);
    }

    /** 현재고·안전재고로부터 재고 상태를 파생한다. */
    public StockStatus status() {
        return StockStatus.of(quantity, safetyStock);
    }

    /**
     * 재고를 조정해 변동량(delta)을 현재고에 반영하고 그 delta를 반환한다.
     * INCREASE +input, DECREASE -input, ADJUST input(실측)-현재고.
     *
     * @throws NoStockChangeException 계산된 변동량이 0일 때(변화 없음)
     * @throws InsufficientStockException 반영 결과 현재고가 음수가 될 때(음수 재고 금지)
     */
    public int adjust(AdjustmentType type, int inputQuantity) {
        int delta = type.delta(this.quantity, inputQuantity);
        if (delta == 0) {
            throw new NoStockChangeException(sku);
        }
        int next = this.quantity + delta;
        if (next < 0) {
            throw new InsufficientStockException(sku, this.quantity, delta);
        }
        this.quantity = next;
        return delta;
    }
}
