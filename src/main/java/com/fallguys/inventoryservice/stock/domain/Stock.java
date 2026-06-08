package com.fallguys.inventoryservice.stock.domain;

import com.fallguys.inventoryservice.stock.domain.exception.InsufficientStockException;
import com.fallguys.inventoryservice.stock.domain.exception.NoStockChangeException;

import lombok.Getter;

/**
 * 재고 애그리거트 루트. (sku × warehouse) 조합으로 식별되는 단일 재고 상태를 표현하며 JPA에 의존하지 않는다.
 * 생성 시점의 불변식(수량·안전재고 ≥ 0, 식별자 필수)을 도메인이 보장한다.
 * 생성·수정자 사번 snapshot, 시각, version은 영속 계층(엔티티 + Auditing)이 관리한다.
 *
 * <p>설계상 <b>가변</b> 애그리거트 루트다(불변 값 객체가 아님, 애그리거트=class+가변 상태).
 * 상태 변경은 {@code adjust()} 한 곳으로만 일어나며, 불변식 검증을 모두 통과한 뒤에야 수량이 바뀌므로 예외 발생 시 상태가 보존된다.
 * 인스턴스는 트랜잭션 범위에서만 쓰이고 스레드 간 공유·캐시되지 않으며, 동시 변경 안전성은 영속 계층의
 * {@code @Version}(낙관락)이 DB 행 수준에서 보장한다(인메모리 불변성으로 대체되지 않는 부분).
 */
@Getter
public class Stock {

    private final Long id;
    private final String sku;
    private final String itemName;
    private final ItemUnit itemUnit;
    private final Long warehouseId;
    private int quantity;
    private final int safetyStock;

    private Stock(Long id, String sku, String itemName, ItemUnit itemUnit, Long warehouseId, int quantity, int safetyStock) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku는 필수입니다.");
        }
        if (itemName == null || itemName.isBlank()) {
            throw new IllegalArgumentException("itemName은 필수입니다.");
        }
        if (itemUnit == null) {
            throw new IllegalArgumentException("itemUnit은 필수입니다.");
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
        this.itemUnit = itemUnit;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.safetyStock = safetyStock;
    }

    /** 신규 재고를 생성한다. id는 영속 시 발급된다. */
    public static Stock create(String sku, String itemName, ItemUnit itemUnit, Long warehouseId, int quantity, int safetyStock) {
        return new Stock(null, sku, itemName, itemUnit, warehouseId, quantity, safetyStock);
    }

    /** 영속 엔티티에서 도메인 모델을 복원한다(조회용). */
    public static Stock of(Long id, String sku, String itemName, ItemUnit itemUnit, Long warehouseId, int quantity, int safetyStock) {
        return new Stock(id, sku, itemName, itemUnit, warehouseId, quantity, safetyStock);
    }

    /** 현재고·안전재고로부터 재고 상태를 파생한다. */
    public StockStatus status() {
        return StockStatus.of(quantity, safetyStock);
    }

    /**
     * 재고를 조정해 변동량(delta)을 현재고에 반영하고 그 delta를 반환한다.
     * INCREASE +input, DECREASE -input, ADJUST input(실측)-현재고.
     *
     * @throws IllegalArgumentException 입력 수량이 음수일 때(HTTP 경로는 DTO가 1차로 막지만, 직접 호출 대비 도메인 백스톱)
     * @throws NoStockChangeException 계산된 변동량이 0일 때(변화 없음)
     * @throws InsufficientStockException 반영 결과 현재고가 음수가 될 때(음수 재고 금지)
     */
    public int adjust(AdjustmentType type, int inputQuantity) {
        if (inputQuantity < 0) { // dto 를 거치지 않았을 때의 방어 코드
            throw new IllegalArgumentException("조정 입력 수량은 0 이상이어야 합니다: " + inputQuantity);
        }
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
