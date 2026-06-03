package com.fallguys.inventoryservice.domain.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.domain.exception.InvalidParameterException;
import com.fallguys.inventoryservice.domain.exception.ParameterViolation;
import com.fallguys.inventoryservice.domain.model.WarehouseType;

class WarehouseSearchQueryTest {

    @Test
    void 모든_파라미터가_없으면_기본값으로_조회조건을_만든다() {
        WarehouseSearchQuery query = WarehouseSearchQuery.of(null, null, null, null);

        assertThat(query.keyword()).isNull();
        assertThat(query.type()).isNull();
        assertThat(query.status()).isEqualTo(StatusFilter.ALL);
        assertThat(query.sort().field()).isEqualTo(WarehouseSortField.CODE);
        assertThat(query.sort().direction()).isEqualTo(SortDirection.ASC);
        assertThat(query.sort().toParam()).isEqualTo("code,asc");
    }

    @Test
    void keyword는_trim되고_빈문자열이면_null이_된다() {
        assertThat(WarehouseSearchQuery.of("  본사 ", null, null, null).keyword()).isEqualTo("본사");
        assertThat(WarehouseSearchQuery.of("   ", null, null, null).keyword()).isNull();
    }

    @Test
    void type은_대소문자를_무시하고_파싱된다() {
        assertThat(WarehouseSearchQuery.of(null, "hq", null, null).type()).isEqualTo(WarehouseType.HQ);
        assertThat(WarehouseSearchQuery.of(null, "DEALER", null, null).type()).isEqualTo(WarehouseType.DEALER);
        assertThat(WarehouseSearchQuery.of(null, "  ", null, null).type()).isNull();
    }

    @Test
    void status는_대소문자를_무시하고_파싱된다() {
        assertThat(WarehouseSearchQuery.of(null, null, "active", null).status()).isEqualTo(StatusFilter.ACTIVE);
        assertThat(WarehouseSearchQuery.of(null, null, "INACTIVE", null).status()).isEqualTo(StatusFilter.INACTIVE);
        assertThat(WarehouseSearchQuery.of(null, null, "ALL", null).status()).isEqualTo(StatusFilter.ALL);
    }

    @Test
    void sort는_field와_direction으로_파싱된다() {
        WarehouseSort sort = WarehouseSearchQuery.of(null, null, null, "name,desc").sort();

        assertThat(sort.field()).isEqualTo(WarehouseSortField.NAME);
        assertThat(sort.direction()).isEqualTo(SortDirection.DESC);
        assertThat(sort.toParam()).isEqualTo("name,desc");
    }

    @Test
    void 허용되지_않는_type이면_INVALID_PARAMETER로_막는다() {
        assertThatThrownBy(() -> WarehouseSearchQuery.of(null, "FACTORY", null, null))
                .isInstanceOfSatisfying(InvalidParameterException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("INVALID_PARAMETER");
                    assertThat(ex.getDetails()).singleElement().satisfies(v -> {
                        assertThat(v.field()).isEqualTo("type");
                        assertThat(v.value()).isEqualTo("FACTORY");
                        assertThat(v.allowed()).containsExactly("HQ", "DEALER");
                    });
                });
    }

    @Test
    void 허용되지_않는_status이면_막는다() {
        assertThatThrownBy(() -> WarehouseSearchQuery.of(null, null, "PAUSED", null))
                .isInstanceOfSatisfying(InvalidParameterException.class, ex ->
                        assertThat(ex.getDetails()).singleElement().satisfies(v -> {
                            assertThat(v.field()).isEqualTo("status");
                            assertThat(v.allowed()).containsExactly("ACTIVE", "INACTIVE", "ALL");
                        }));
    }

    @Test
    void sort의_field가_허용밖이면_막는다() {
        assertThatThrownBy(() -> WarehouseSearchQuery.of(null, null, null, "address,asc"))
                .isInstanceOfSatisfying(InvalidParameterException.class, ex ->
                        assertThat(ex.getDetails()).singleElement().satisfies(v -> {
                            assertThat(v.field()).isEqualTo("sort");
                            assertThat(v.allowed()).containsExactly("code", "name", "type", "createdAt");
                        }));
    }

    @Test
    void sort의_direction이_허용밖이면_막는다() {
        assertThatThrownBy(() -> WarehouseSearchQuery.of(null, null, null, "code,up"))
                .isInstanceOfSatisfying(InvalidParameterException.class, ex ->
                        assertThat(ex.getDetails()).singleElement().satisfies(v -> {
                            assertThat(v.field()).isEqualTo("sort");
                            assertThat(v.allowed()).containsExactly("asc", "desc");
                        }));
    }

    @Test
    void sort_포맷이_field_direction_쌍이_아니면_막는다() {
        assertThatThrownBy(() -> WarehouseSearchQuery.of(null, null, null, "code"))
                .isInstanceOfSatisfying(InvalidParameterException.class, ex ->
                        assertThat(ex.getDetails()).singleElement().satisfies(v -> {
                            assertThat(v.field()).isEqualTo("sort");
                            assertThat(v.allowed()).containsExactly("{property},{direction}");
                        }));
    }

    @Test
    void sort의_field와_direction이_모두_틀리면_위반이_2건_쌓인다() {
        assertThatThrownBy(() -> WarehouseSearchQuery.of(null, null, null, "foo,bar"))
                .isInstanceOfSatisfying(InvalidParameterException.class, ex ->
                        assertThat(ex.getDetails()).hasSize(2));
    }

    @Test
    void 여러_파라미터가_동시에_틀리면_위반을_모두_누적한다() {
        assertThatThrownBy(() -> WarehouseSearchQuery.of(null, "BAD", "BAD", "bad,bad"))
                .isInstanceOfSatisfying(InvalidParameterException.class, ex -> {
                    assertThat(ex.getDetails()).extracting(ParameterViolation::field)
                            .contains("type", "status", "sort");
                    // type 1 + status 1 + sort(field+direction) 2 = 4건
                    assertThat(ex.getDetails()).hasSize(4);
                });
    }
}
