package com.fallguys.inventoryservice.stock.domain.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.exception.ParameterViolation;

class WarehouseStockQueryTest {

    @Test
    void of_정상이면_warehouseCode를_trim하고_skus를_중복제거한다() {
        WarehouseStockQuery query = WarehouseStockQuery.of("  WH-SE-001  ", "A, B ,A, C");

        assertThat(query.warehouseCode()).isEqualTo("WH-SE-001");
        assertThat(query.skus()).containsExactly("A", "B", "C");
    }

    @Test
    void of_warehouseCode가_없으면_INVALID_PARAMETER이고_field가_warehouseCode다() {
        assertThatThrownBy(() -> WarehouseStockQuery.of("  ", "A"))
                .isInstanceOfSatisfying(InvalidParameterException.class, ex ->
                        assertThat(ex.getDetails()).extracting(ParameterViolation::field).contains("warehouseCode"));
    }

    @Test
    void of_skus가_없으면_INVALID_PARAMETER이고_field가_skus다() {
        assertThatThrownBy(() -> WarehouseStockQuery.of("WH-SE-001", "  "))
                .isInstanceOfSatisfying(InvalidParameterException.class, ex ->
                        assertThat(ex.getDetails()).extracting(ParameterViolation::field).contains("skus"));
    }

    @Test
    void of_skus가_50개를_넘으면_INVALID_PARAMETER다() {
        String csv = IntStream.rangeClosed(1, 51).mapToObj(i -> "SKU-" + i).collect(Collectors.joining(","));

        assertThatThrownBy(() -> WarehouseStockQuery.of("WH-SE-001", csv))
                .isInstanceOf(InvalidParameterException.class);
    }

    @Test
    void of_warehouseCode와_skus가_모두_없으면_위반이_둘다_모인다() {
        assertThatThrownBy(() -> WarehouseStockQuery.of(null, null))
                .isInstanceOfSatisfying(InvalidParameterException.class, ex ->
                        assertThat(ex.getDetails()).extracting(ParameterViolation::field)
                                .contains("warehouseCode", "skus"));
    }

    @Test
    void of_정확히_50개는_허용된다() {
        String csv = IntStream.rangeClosed(1, 50).mapToObj(i -> "SKU-" + i).collect(Collectors.joining(","));

        WarehouseStockQuery query = WarehouseStockQuery.of("WH-SE-001", csv);

        assertThat(query.skus()).hasSize(50);
    }
}
