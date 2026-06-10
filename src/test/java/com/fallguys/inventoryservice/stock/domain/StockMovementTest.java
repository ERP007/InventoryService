package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class StockMovementTest {

    // --- createAdjustment 성공 경로 ---

    @Test
    void INCREASE는_양수_변동량으로_생성되며_원천문서와_식별자는_비어있다() {
        StockMovement m = StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 5, MovementType.INCREASE, MovementReason.FOUND, 56,
                "입고 누락분", "HMC0001", "홍길동");

        assertThat(m.getId()).isNull();
        assertThat(m.getPerformedAt()).isNull();
        assertThat(m.getSourceRef()).isNull();
        assertThat(m.getSourceLineNo()).isNull();
        assertThat(m.getDelta()).isEqualTo(5);
        assertThat(m.getType()).isEqualTo(MovementType.INCREASE);
        assertThat(m.getReason()).isEqualTo(MovementReason.FOUND);
        assertThat(m.getStockAfter()).isEqualTo(56);
        assertThat(m.getItemName()).isEqualTo("엔진오일 필터");
        assertThat(m.getItemUnit()).isEqualTo(ItemUnit.EA);
        assertThat(m.getExecutorEmpNo()).isEqualTo("HMC0001");
        assertThat(m.getExecutorName()).isEqualTo("홍길동");
    }

    @Test
    void DECREASE는_음수_변동량으로_생성된다() {
        StockMovement m = StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, -3, MovementType.DECREASE, MovementReason.DAMAGE, 48,
                "파손", "HMC0001", "홍길동");

        assertThat(m.getDelta()).isEqualTo(-3);
        assertThat(m.getType()).isEqualTo(MovementType.DECREASE);
    }

    @Test
    void ADJUST는_음수_변동량도_허용된다() {
        StockMovement m = StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, -7, MovementType.ADJUST, MovementReason.LOST, 40,
                "실사 차이", "HMC0001", "홍길동");

        assertThat(m.getDelta()).isEqualTo(-7);
        assertThat(m.getType()).isEqualTo(MovementType.ADJUST);
    }

    @Test
    void ADJUST는_양수_변동량도_허용된다() {
        StockMovement m = StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 7, MovementType.ADJUST, MovementReason.FOUND, 60,
                "실사 차이", "HMC0001", "홍길동");

        assertThat(m.getDelta()).isEqualTo(7);
    }

    @Test
    void reason과_note는_null이어도_생성된다() {
        StockMovement m = StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 5, MovementType.INCREASE, null, 56, null, "HMC0001",
                "홍길동");

        assertThat(m.getReason()).isNull();
        assertThat(m.getNote()).isNull();
    }

    // --- createAdjustment 유형/부호 검증 ---

    @Test
    void INCREASE인데_변동량이_0이하면_예외() {
        assertThatThrownBy(() -> StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 0, MovementType.INCREASE, MovementReason.FOUND, 51,
                null, "HMC0001", "홍길동"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, -1, MovementType.INCREASE, MovementReason.FOUND, 50,
                null, "HMC0001", "홍길동"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void DECREASE인데_변동량이_0이상이면_예외() {
        assertThatThrownBy(() -> StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 0, MovementType.DECREASE, MovementReason.DAMAGE, 51,
                null, "HMC0001", "홍길동"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 1, MovementType.DECREASE, MovementReason.DAMAGE, 52,
                null, "HMC0001", "홍길동"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ADJUST인데_변동량이_0이면_예외() {
        assertThatThrownBy(() -> StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 0, MovementType.ADJUST, MovementReason.FOUND, 51,
                null, "HMC0001", "홍길동"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 조정유형이_아니면_예외() {
        assertThatThrownBy(() -> StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 5, MovementType.INBOUND, null, 56, null, "HMC0001",
                "홍길동"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, -5, MovementType.OUTBOUND, null, 46, null, "HMC0001",
                "홍길동"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 유형이_null이면_예외() {
        assertThatThrownBy(() -> StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 5, null, null, 56, null, "HMC0001", "홍길동"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- createInbound (입고) ---

    @Test
    void createInbound는_type_INBOUND_reason_null로_원천문서와_함께_생성된다() {
        StockMovement m = StockMovement.createInbound(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 30, "PO-2026-0012", 1, 130, "HMC1001", "김본사");

        assertThat(m.getId()).isNull();
        assertThat(m.getType()).isEqualTo(MovementType.INBOUND);
        assertThat(m.getReason()).isNull();
        assertThat(m.getNote()).isNull();
        assertThat(m.getDelta()).isEqualTo(30);
        assertThat(m.getStockAfter()).isEqualTo(130);
        assertThat(m.getSourceRef()).isEqualTo("PO-2026-0012");
        assertThat(m.getSourceLineNo()).isEqualTo(1);
        assertThat(m.getExecutorEmpNo()).isEqualTo("HMC1001");
        assertThat(m.getExecutorName()).isEqualTo("김본사");
    }

    @Test
    void createInbound_delta가_0이하면_예외() {
        assertThatThrownBy(() -> StockMovement.createInbound(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 0, "PO-1", 1, 100, "HMC1001", "김본사"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- of() 복원 + 생성자 불변식 ---

    @Test
    void of로_전체_필드를_복원한다() {
        Instant at = Instant.parse("2026-05-20T14:22:00Z");
        StockMovement m = StockMovement.of(88231L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, -3,
                MovementType.OUTBOUND, null, "SO-202605-00001", 4, 48, "고객 출고", "HMC0001", "홍길동", at);

        assertThat(m.getId()).isEqualTo(88231L);
        assertThat(m.getType()).isEqualTo(MovementType.OUTBOUND);
        assertThat(m.getSourceRef()).isEqualTo("SO-202605-00001");
        assertThat(m.getSourceLineNo()).isEqualTo(4);
        assertThat(m.getPerformedAt()).isEqualTo(at);
    }

    @Test
    void sku가_null이면_예외() {
        assertThatThrownBy(() -> StockMovement.of(1L, null, "엔진오일 필터", ItemUnit.EA, 2L, 5, MovementType.INCREASE,
                null, null, null, 5, null, "HMC0001", "홍길동", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sku가_공백이면_예외() {
        assertThatThrownBy(() -> StockMovement.of(1L, " ", "엔진오일 필터", ItemUnit.EA, 2L, 5, MovementType.INCREASE,
                null, null, null, 5, null, "HMC0001", "홍길동", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void warehouseId가_null이면_예외() {
        assertThatThrownBy(() -> StockMovement.of(1L, "SKU", "엔진오일 필터", ItemUnit.EA, null, 5, MovementType.INCREASE,
                null, null, null, 5, null, "HMC0001", "홍길동", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void type이_null이면_예외_of() {
        assertThatThrownBy(() -> StockMovement.of(1L, "SKU", "엔진오일 필터", ItemUnit.EA, 2L, 5, null,
                null, null, null, 5, null, "HMC0001", "홍길동", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delta가_0이면_예외_of() {
        assertThatThrownBy(() -> StockMovement.of(1L, "SKU", "엔진오일 필터", ItemUnit.EA, 2L, 0, MovementType.ADJUST,
                null, null, null, 5, null, "HMC0001", "홍길동", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stockAfter가_음수면_예외() {
        assertThatThrownBy(() -> StockMovement.of(1L, "SKU", "엔진오일 필터", ItemUnit.EA, 2L, 5, MovementType.INCREASE,
                null, null, null, -1, null, "HMC0001", "홍길동", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executorEmpNo가_null이면_예외() {
        assertThatThrownBy(() -> StockMovement.of(1L, "SKU", "엔진오일 필터", ItemUnit.EA, 2L, 5, MovementType.INCREASE,
                null, null, null, 5, null, null, "홍길동", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executorEmpNo가_공백이면_예외() {
        assertThatThrownBy(() -> StockMovement.of(1L, "SKU", "엔진오일 필터", ItemUnit.EA, 2L, 5, MovementType.INCREASE,
                null, null, null, 5, null, "  ", "홍길동", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executorName이_null이면_예외() {
        assertThatThrownBy(() -> StockMovement.of(1L, "SKU", "엔진오일 필터", ItemUnit.EA, 2L, 5, MovementType.INCREASE,
                null, null, null, 5, null, "HMC0001", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
