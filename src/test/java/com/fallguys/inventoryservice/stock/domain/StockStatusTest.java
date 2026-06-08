package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StockStatusTest {

    @Test
    void 수량이_0이면_OUT() {
        assertThat(StockStatus.of(0, 50)).isEqualTo(StockStatus.OUT);
    }

    @Test
    void 수량이_안전재고_미만이면_LOW() {
        assertThat(StockStatus.of(48, 50)).isEqualTo(StockStatus.LOW);
    }

    @Test
    void 수량이_안전재고와_같으면_NORMAL() {
        assertThat(StockStatus.of(50, 50)).isEqualTo(StockStatus.NORMAL);
    }

    @Test
    void 수량이_안전재고_초과면_NORMAL() {
        assertThat(StockStatus.of(120, 50)).isEqualTo(StockStatus.NORMAL);
    }

    @Test
    void 안전재고가_0이고_수량이_있으면_NORMAL() {
        assertThat(StockStatus.of(5, 0)).isEqualTo(StockStatus.NORMAL);
    }

    @Test
    void 수량과_안전재고가_모두_0이면_OUT() {
        assertThat(StockStatus.of(0, 0)).isEqualTo(StockStatus.OUT);
    }
}
