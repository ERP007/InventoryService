package com.fallguys.inventoryservice.stock.domain.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.stock.domain.MovementType;

class MovementSearchQueryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 7);

    @Test
    void 기본값은_page1_size20_occurredAt_desc_최근30일_type전체다() {
        MovementSearchQuery query = MovementSearchQuery.of(null, null, null, null, null, null, null, null, TODAY);

        assertThat(query.keyword()).isNull();
        assertThat(query.warehouseCodes()).isEmpty();
        assertThat(query.type()).isNull();
        assertThat(query.from()).isEqualTo(LocalDate.of(2026, 5, 8));
        assertThat(query.to()).isEqualTo(TODAY);
        assertThat(query.sortField()).isEqualTo(MovementSortField.OCCURRED_AT);
        assertThat(query.sortDirection()).isEqualTo(SortDirection.DESC);
        assertThat(query.page()).isEqualTo(1);
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.hasWarehouseFilter()).isFalse();
    }

    @Test
    void keyword는_trim하고_빈값이면_null이다() {
        assertThat(MovementSearchQuery.of("  엔진  ", null, null, null, null, null, null, null, TODAY).keyword())
                .isEqualTo("엔진");
        assertThat(MovementSearchQuery.of("   ", null, null, null, null, null, null, null, TODAY).keyword())
                .isNull();
    }

    @Test
    void warehouseCodes는_콤마로_분리하고_공백을_제거한다() {
        MovementSearchQuery query =
                MovementSearchQuery.of(null, " WH-SE-001 , HQ-001 ,, ", null, null, null, null, null, null, TODAY);

        assertThat(query.warehouseCodes()).containsExactly("WH-SE-001", "HQ-001");
        assertThat(query.hasWarehouseFilter()).isTrue();
    }

    @Test
    void type은_유효한_유형을_허용하고_그_외는_400이다() {
        assertThat(MovementSearchQuery.of(null, null, "outbound", null, null, null, null, null, TODAY).type())
                .isEqualTo(MovementType.OUTBOUND);

        assertThatThrownBy(() -> MovementSearchQuery.of(null, null, "WRONG", null, null, null, null, null, TODAY))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void from_to를_지정하면_그_값을_쓴다() {
        MovementSearchQuery query =
                MovementSearchQuery.of(null, null, null, "2026-01-01", "2026-03-31", null, null, null, TODAY);

        assertThat(query.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(query.to()).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void from이_to보다_뒤면_400이다() {
        assertThatThrownBy(() ->
                MovementSearchQuery.of(null, null, null, "2026-06-10", "2026-06-01", null, null, null, TODAY))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void 날짜_형식이_잘못되면_400이다() {
        assertThatThrownBy(() ->
                MovementSearchQuery.of(null, null, null, "2026-13-99", null, null, null, null, TODAY))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void sort는_화이트리스트_속성과_방향만_허용한다() {
        MovementSearchQuery query =
                MovementSearchQuery.of(null, null, null, null, null, "delta,asc", null, null, TODAY);
        assertThat(query.sortField()).isEqualTo(MovementSortField.DELTA);
        assertThat(query.sortDirection()).isEqualTo(SortDirection.ASC);

        assertThatThrownBy(() ->
                MovementSearchQuery.of(null, null, null, null, null, "price,asc", null, null, TODAY))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void page는_1미만이면_400이다() {
        assertThatThrownBy(() -> MovementSearchQuery.of(null, null, null, null, null, null, 0, null, TODAY))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void size는_20_50_100만_허용한다() {
        assertThat(MovementSearchQuery.of(null, null, null, null, null, null, null, 50, TODAY).size()).isEqualTo(50);

        assertThatThrownBy(() -> MovementSearchQuery.of(null, null, null, null, null, null, null, 33, TODAY))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void withWarehouseCodes는_창고필터만_교체한_사본을_만든다() {
        MovementSearchQuery base =
                MovementSearchQuery.of("엔진", "HQ-001", "INBOUND", null, null, "delta,asc", 2, 50, TODAY);

        MovementSearchQuery branched = base.withWarehouseCodes(List.of("WH-SE-001"));

        assertThat(branched.warehouseCodes()).containsExactly("WH-SE-001");
        assertThat(branched.keyword()).isEqualTo("엔진");
        assertThat(branched.type()).isEqualTo(MovementType.INBOUND);
        assertThat(branched.sortField()).isEqualTo(MovementSortField.DELTA);
        assertThat(branched.page()).isEqualTo(2);
        assertThat(branched.size()).isEqualTo(50);
    }

    @Test
    void 여러_위반은_한_번에_누적되어_보고된다() {
        assertThatThrownBy(() ->
                MovementSearchQuery.of(null, null, "WRONG", "2026-13-01", null, "price,up", -1, 7, TODAY))
                .isInstanceOf(InvalidParameterException.class)
                .satisfies(thrown -> assertThat(((InvalidParameterException) thrown).getDetails())
                        .extracting("field")
                        .contains("type", "from", "sort", "page", "size"));
    }
}
