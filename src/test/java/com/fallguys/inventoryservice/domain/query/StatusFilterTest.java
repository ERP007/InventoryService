package com.fallguys.inventoryservice.domain.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StatusFilterTest {

    @Test
    void toActiveFilter는_상태를_active_불리언으로_변환한다() {
        assertThat(StatusFilter.ACTIVE.toActiveFilter()).isTrue();
        assertThat(StatusFilter.INACTIVE.toActiveFilter()).isFalse();
        assertThat(StatusFilter.ALL.toActiveFilter()).isNull();
    }

    @Test
    void WarehouseSort는_널_인자를_거부한다() {
        assertThatThrownBy(() -> new WarehouseSort(null, SortDirection.ASC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WarehouseSort(WarehouseSortField.CODE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
