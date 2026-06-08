package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.stock.domain.exception.InsufficientStockException;
import com.fallguys.inventoryservice.stock.domain.exception.NoStockChangeException;

class StockTest {

    @Test
    void 생성_성공시_필드와_파생status를_노출하고_id는_null이다() {
        Stock stock = Stock.create("HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 48, 50);

        assertThat(stock.getId()).isNull();
        assertThat(stock.getSku()).isEqualTo("HMC-EN-00214");
        assertThat(stock.getItemName()).isEqualTo("엔진오일 필터");
        assertThat(stock.getItemUnit()).isEqualTo(ItemUnit.EA);
        assertThat(stock.getWarehouseId()).isEqualTo(2L);
        assertThat(stock.getQuantity()).isEqualTo(48);
        assertThat(stock.getSafetyStock()).isEqualTo(50);
        assertThat(stock.status()).isEqualTo(StockStatus.LOW);
    }

    @Test
    void of로_복원하면_id를_보존한다() {
        Stock stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 120, 50);

        assertThat(stock.getId()).isEqualTo(1001L);
        assertThat(stock.status()).isEqualTo(StockStatus.NORMAL);
    }

    @Test
    void 수량이_음수면_예외() {
        assertThatThrownBy(() -> Stock.create("SKU", "부품", ItemUnit.EA, 1L, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 안전재고가_음수면_예외() {
        assertThatThrownBy(() -> Stock.create("SKU", "부품", ItemUnit.EA, 1L, 10, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sku가_공백이면_예외() {
        assertThatThrownBy(() -> Stock.create("  ", "부품", ItemUnit.EA, 1L, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemName이_공백이면_예외() {
        assertThatThrownBy(() -> Stock.create("SKU", "", ItemUnit.EA, 1L, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void warehouseId가_null이면_예외() {
        assertThatThrownBy(() -> Stock.create("SKU", "부품", ItemUnit.EA, null, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void INCREASE는_현재고를_증가시키고_양수_delta를_반환한다() {
        Stock stock = Stock.of(1001L, "SKU-1", "부품", ItemUnit.EA, 1L, 50, 40);

        int delta = stock.adjust(AdjustmentType.INCREASE, 10);

        assertThat(delta).isEqualTo(10);
        assertThat(stock.getQuantity()).isEqualTo(60);
        assertThat(stock.status()).isEqualTo(StockStatus.NORMAL);
    }

    @Test
    void DECREASE는_현재고를_감소시키고_음수_delta를_반환한다() {
        Stock stock = Stock.of(1001L, "SKU-1", "부품", ItemUnit.EA, 1L, 50, 40);

        int delta = stock.adjust(AdjustmentType.DECREASE, 12);

        assertThat(delta).isEqualTo(-12);
        assertThat(stock.getQuantity()).isEqualTo(38);
        assertThat(stock.status()).isEqualTo(StockStatus.LOW);
    }

    @Test
    void ADJUST는_실측과_현재고의_차이를_delta로_반영한다() {
        Stock stock = Stock.of(1001L, "SKU-1", "부품", ItemUnit.EA, 1L, 50, 40);

        int delta = stock.adjust(AdjustmentType.ADJUST, 45);

        assertThat(delta).isEqualTo(-5);
        assertThat(stock.getQuantity()).isEqualTo(45);
    }

    @Test
    void ADJUST_실측이_현재고와_같으면_NoStockChangeException() {
        Stock stock = Stock.of(1001L, "SKU-1", "부품", ItemUnit.EA, 1L, 50, 40);

        assertThatThrownBy(() -> stock.adjust(AdjustmentType.ADJUST, 50))
                .isInstanceOf(NoStockChangeException.class);
    }

    @Test
    void DECREASE_차감이_현재고를_초과하면_InsufficientStockException이고_현재고는_불변이다() {
        Stock stock = Stock.of(1001L, "SKU-1", "부품", ItemUnit.EA, 1L, 50, 40);

        assertThatThrownBy(() -> stock.adjust(AdjustmentType.DECREASE, 60))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(stock.getQuantity()).isEqualTo(50);
    }

    @Test
    void ADJUST_0으로_실측하면_전량_차감되어_OUT이_된다() {
        Stock stock = Stock.of(1001L, "SKU-1", "부품", ItemUnit.EA, 1L, 50, 40);

        int delta = stock.adjust(AdjustmentType.ADJUST, 0);

        assertThat(delta).isEqualTo(-50);
        assertThat(stock.getQuantity()).isZero();
        assertThat(stock.status()).isEqualTo(StockStatus.OUT);
    }

    @Test
    void 음수_입력수량은_IllegalArgumentException이고_현재고는_불변이다() {
        Stock stock = Stock.of(1001L, "SKU-1", "부품", ItemUnit.EA, 1L, 50, 40);

        assertThatThrownBy(() -> stock.adjust(AdjustmentType.DECREASE, -5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(stock.getQuantity()).isEqualTo(50);
    }
}
