package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StockTest {

    @Test
    void 생성_성공시_필드와_파생status를_노출하고_id는_null이다() {
        Stock stock = Stock.create("HMC-EN-00214", "엔진오일 필터", 2L, 48, 50);

        assertThat(stock.getId()).isNull();
        assertThat(stock.getSku()).isEqualTo("HMC-EN-00214");
        assertThat(stock.getItemName()).isEqualTo("엔진오일 필터");
        assertThat(stock.getWarehouseId()).isEqualTo(2L);
        assertThat(stock.getQuantity()).isEqualTo(48);
        assertThat(stock.getSafetyStock()).isEqualTo(50);
        assertThat(stock.status()).isEqualTo(StockStatus.LOW);
    }

    @Test
    void of로_복원하면_id를_보존한다() {
        Stock stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);

        assertThat(stock.getId()).isEqualTo(1001L);
        assertThat(stock.status()).isEqualTo(StockStatus.NORMAL);
    }

    @Test
    void 수량이_음수면_예외() {
        assertThatThrownBy(() -> Stock.create("SKU", "부품", 1L, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 안전재고가_음수면_예외() {
        assertThatThrownBy(() -> Stock.create("SKU", "부품", 1L, 10, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sku가_공백이면_예외() {
        assertThatThrownBy(() -> Stock.create("  ", "부품", 1L, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemName이_공백이면_예외() {
        assertThatThrownBy(() -> Stock.create("SKU", "", 1L, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void warehouseId가_null이면_예외() {
        assertThatThrownBy(() -> Stock.create("SKU", "부품", null, 10, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
