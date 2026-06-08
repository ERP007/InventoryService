package com.fallguys.inventoryservice.stock.domain;

import java.time.Instant;

import lombok.Getter;

/**
 * 재고 이동 이력(append-only 원장 항목). 한 번 기록되면 수정·삭제되지 않는 불변 모델이며 JPA에 의존하지 않는다.
 * (sku × warehouse)의 단일 변동을 표현한다.
 *
 * 부품명(itemName)·단위(itemUnit)·수행자 이름(executorName)은 변동 당시 상황을 박제한 <b>스냅샷</b>이다.
 * Item/User 마스터와의 실시간 일치는 보장하지 않으며(마스터가 바뀌어도 과거 이력은 그대로), 이력 화면은 이 스냅샷을 그대로 보여준다.
 *
 * 불변식:
 * - 식별·스냅샷 필드(sku·itemName·itemUnit·warehouseId·type·executorEmpNo·executorName) 필수
 * - 변동량(delta) != 0 (변화 없는 이력은 남기지 않는다 — 0 보정은 상위에서 NO_STOCK_CHANGE로 거른다)
 * - 변동 후 잔량(stockAfter) >= 0 (음수 재고 금지)
 *
 * id·performedAt은 영속 시점에 발급/기록된다(신규 생성 시 null).
 * sourceRef·sourceLineNo는 PO/SO 등 원천 문서가 있을 때만 채워진다(조정은 null — 표시용 'ADJ-{id}'는 응답에서 합성).
 */
@Getter
public class StockMovement {

    private final Long id;
    private final String sku;
    private final String itemName;
    private final ItemUnit itemUnit;
    private final Long warehouseId;
    private final int delta;
    private final MovementType type;
    private final MovementReason reason;
    private final String sourceRef;
    private final Integer sourceLineNo;
    private final int stockAfter;
    private final String note;
    private final String executorEmpNo;
    private final String executorName;
    private final Instant performedAt;

    private StockMovement(Long id, String sku, String itemName, ItemUnit itemUnit, Long warehouseId, int delta,
                          MovementType type, MovementReason reason, String sourceRef, Integer sourceLineNo,
                          int stockAfter, String note, String executorEmpNo, String executorName,
                          Instant performedAt) {
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
        if (type == null) {
            throw new IllegalArgumentException("type은 필수입니다.");
        }
        if (delta == 0) {
            throw new IllegalArgumentException("변동량(delta)은 0일 수 없습니다.");
        }
        if (stockAfter < 0) {
            throw new IllegalArgumentException("변동 후 잔량(stockAfter)은 0 이상이어야 합니다.");
        }
        if (executorEmpNo == null || executorEmpNo.isBlank()) {
            throw new IllegalArgumentException("executorEmpNo는 필수입니다.");
        }
        if (executorName == null || executorName.isBlank()) {
            throw new IllegalArgumentException("executorName은 필수입니다.");
        }
        this.id = id;
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
        this.performedAt = performedAt;
    }

    /**
     * 재고 조정(INCREASE/DECREASE/ADJUST)으로 신규 이동 이력을 생성한다. 원천 문서가 없어 sourceRef·sourceLineNo는 null이다.
     * itemName·itemUnit은 조정 대상 stock 행에서, executorName은 수행자 토큰에서 박제한 스냅샷이다.
     * id·performedAt은 영속 시 채워진다.
     *
     * @throws IllegalArgumentException 조정 유형이 아니거나 유형과 변동량 부호가 맞지 않을 때
     *         (INCREASE는 양수, DECREASE는 음수, ADJUST는 0이 아닌 값)
     */
    public static StockMovement createAdjustment(String sku, String itemName, ItemUnit itemUnit, Long warehouseId,
                                                 int delta, MovementType type, MovementReason reason, int stockAfter,
                                                 String note, String executorEmpNo, String executorName) {
        validateAdjustmentDelta(type, delta);
        return new StockMovement(null, sku, itemName, itemUnit, warehouseId, delta, type, reason, null, null,
                stockAfter, note, executorEmpNo, executorName, null);
    }

    /** 영속 엔티티에서 도메인 모델을 복원한다(조회). */
    public static StockMovement of(Long id, String sku, String itemName, ItemUnit itemUnit, Long warehouseId,
                                   int delta, MovementType type, MovementReason reason, String sourceRef,
                                   Integer sourceLineNo, int stockAfter, String note, String executorEmpNo,
                                   String executorName, Instant performedAt) {
        return new StockMovement(id, sku, itemName, itemUnit, warehouseId, delta, type, reason, sourceRef,
                sourceLineNo, stockAfter, note, executorEmpNo, executorName, performedAt);
    }

    /**
     * 조정 유형과 변동량 부호의 정합을 검증한다(생성자의 delta != 0 검증 이전 단계).
     * INCREASE는 양수, DECREASE는 음수, ADJUST는 부호 무관(0 여부는 생성자가 검증).
     */
    private static void validateAdjustmentDelta(MovementType type, int delta) {
        if (type == null || !type.isAdjustment()) {
            throw new IllegalArgumentException("조정 유형(INCREASE/DECREASE/ADJUST)만 허용됩니다: " + type);
        }
        if (type == MovementType.INCREASE && delta <= 0) {
            throw new IllegalArgumentException("INCREASE의 변동량은 양수여야 합니다: " + delta);
        }
        if (type == MovementType.DECREASE && delta >= 0) {
            throw new IllegalArgumentException("DECREASE의 변동량은 음수여야 합니다: " + delta);
        }
    }
}
