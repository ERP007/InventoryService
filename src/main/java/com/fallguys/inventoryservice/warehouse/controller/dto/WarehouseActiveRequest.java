package com.fallguys.inventoryservice.warehouse.controller.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 창고 활성 상태 전환 요청. active와 낙관적 락용 version을 받는다.
 * active는 Boolean(객체)으로 받아 누락(null)을 @NotNull로 검출한다.
 *
 * @param active  전환할 활성 상태(필수)
 * @param version 낙관적 락 버전(필수)
 */
public record WarehouseActiveRequest(

        @NotNull(message = "active는 필수입니다.")
        Boolean active,

        @NotNull(message = "version은 필수입니다.")
        Long version
) {
}
