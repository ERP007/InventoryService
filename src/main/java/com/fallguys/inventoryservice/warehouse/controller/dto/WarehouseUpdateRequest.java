package com.fallguys.inventoryservice.warehouse.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 창고 수정 요청. 변경 가능 항목과 낙관적 락용 version을 받는다.
 * code는 불변이라 검증 대상이 아니며, 포함되면 Command가 거부한다(WAREHOUSE_CODE_IMMUTABLE).
 * 문자열은 compact constructor에서 trim하며, 주소는 빈 값이면 null로 정규화한다.
 *
 * @param code     변경 불가 — 포함 시 거부용으로만 받는다(검증 없음)
 * @param name     창고명(필수)
 * @param type     창고 유형 문자열(필수, HQ/DEALER — 값 검증은 Command)
 * @param branchId 소속 지점 PK(DEALER 필수·HQ 불가는 도메인이 검증)
 * @param address  주소(선택)
 * @param version  낙관적 락 버전(필수)
 */
public record WarehouseUpdateRequest(

        String code,

        @NotBlank(message = "창고명은 필수입니다.")
        @Size(max = 100, message = "창고명은 100자를 초과할 수 없습니다.")
        String name,

        @NotBlank(message = "창고 유형은 필수입니다.")
        String type,

        Long branchId,

        @Size(max = 255, message = "주소는 255자를 초과할 수 없습니다.")
        String address,

        @NotNull(message = "version은 필수입니다.")
        Long version
) {

    public WarehouseUpdateRequest {
        name = name == null ? null : name.trim();
        type = type == null ? null : type.trim();
        address = (address == null || address.isBlank()) ? null : address.trim();
    }
}
