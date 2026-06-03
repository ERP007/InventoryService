package com.fallguys.inventoryservice.controller.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.domain.model.WarehouseType;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

class WarehouseListResponseTest {

    @Test
    void 읽기모델_목록을_응답_DTO로_매핑하고_totalElements를_채운다() {
        WarehouseSummary hq = new WarehouseSummary(
                1L, "HQ-001", "본사 중앙창고", WarehouseType.HQ, null, true,
                Instant.parse("2024-01-15T09:00:00Z"), Instant.parse("2024-01-15T09:00:00Z"));
        WarehouseSummary dealer = new WarehouseSummary(
                2L, "HW-SE-001", "서울 1창고", WarehouseType.DEALER, "서울 강남지점", true,
                Instant.parse("2024-03-10T09:00:00Z"), Instant.parse("2025-11-02T14:30:00Z"));

        WarehouseListResponse response = WarehouseListResponse.from(List.of(hq, dealer), "code,asc");

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.sort()).isEqualTo("code,asc");
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).branchName()).isNull();
        assertThat(response.content().get(1).branchName()).isEqualTo("서울 강남지점");
        assertThat(response.content().get(1).code()).isEqualTo("HW-SE-001");
    }

    @Test
    void 빈_목록이면_빈_content와_0건을_반환한다() {
        WarehouseListResponse response = WarehouseListResponse.from(List.of(), "name,desc");

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.sort()).isEqualTo("name,desc");
    }
}
