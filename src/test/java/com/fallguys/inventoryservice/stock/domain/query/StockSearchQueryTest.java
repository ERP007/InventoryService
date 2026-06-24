package com.fallguys.inventoryservice.stock.domain.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.stock.domain.StockStatus;

class StockSearchQueryTest {

    @Test
    void 기본값은_page1_size20_name_asc_빈_창고필터_null_status다() {
        StockSearchQuery query = StockSearchQuery.of(null, null, null, null, null, null);

        assertThat(query.keyword()).isNull();
        assertThat(query.warehouseCodes()).isEmpty();
        assertThat(query.status()).isNull();
        assertThat(query.sortField()).isEqualTo(StockSortField.NAME);
        assertThat(query.sortDirection()).isEqualTo(SortDirection.ASC);
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.hasWarehouseFilter()).isFalse();
    }

    @Test
    void keyword는_trim하고_빈값이면_null이다() {
        assertThat(StockSearchQuery.of("  엔진  ", null, null, null, null, null).keyword()).isEqualTo("엔진");
        assertThat(StockSearchQuery.of("   ", null, null, null, null, null).keyword()).isNull();
    }

    @Test
    void warehouseCodes는_콤마로_분리하고_공백을_제거한다() {
        StockSearchQuery query = StockSearchQuery.of(null, " WH-SE-001 , HQ-001 ,, ", null, null, null, null);

        assertThat(query.warehouseCodes()).containsExactly("WH-SE-001", "HQ-001");
        assertThat(query.hasWarehouseFilter()).isTrue();
    }

    @Test
    void status는_NORMAL_LOW_OUT을_허용하고_그_외는_400이다() {
        assertThat(StockSearchQuery.of(null, null, "low", null, null, null).status()).isEqualTo(StockStatus.LOW);

        assertThatThrownBy(() -> StockSearchQuery.of(null, null, "WRONG", null, null, null))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void sort는_화이트리스트_속성과_방향만_허용한다() {
        StockSearchQuery query = StockSearchQuery.of(null, null, null, "safetyRatio,desc", null, null);
        assertThat(query.sortField()).isEqualTo(StockSortField.SAFETY_RATIO);
        assertThat(query.sortDirection()).isEqualTo(SortDirection.DESC);

        assertThatThrownBy(() -> StockSearchQuery.of(null, null, null, "price,asc", null, null))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void page는_1미만이면_400이다() {
        assertThatThrownBy(() -> StockSearchQuery.of(null, null, null, null, 0, null))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void size는_20_50_100만_허용한다() {
        assertThat(StockSearchQuery.of(null, null, null, null, null, 50).size()).isEqualTo(50);

        assertThatThrownBy(() -> StockSearchQuery.of(null, null, null, null, null, 33))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void 여러_위반은_한_번에_누적되어_보고된다() {
        assertThatThrownBy(() -> StockSearchQuery.of(null, null, "WRONG", "price,up", -1, 7))
                .isInstanceOf(InvalidParameterException.class)
                .satisfies(thrown -> assertThat(((InvalidParameterException) thrown).getDetails())
                        .extracting("field")
                        .contains("status", "sort", "page", "size"));
    }

    @Test
    void includeInactive는_미지정이면_false이고_지정값을_따른다() {
        assertThat(StockSearchQuery.of(null, null, null, null, null, null).includeInactive()).isFalse();
        assertThat(StockSearchQuery.of(null, null, null, null, null, null, true).includeInactive()).isTrue();
        assertThat(StockSearchQuery.of(null, null, null, null, null, null, false).includeInactive()).isFalse();
    }
}
