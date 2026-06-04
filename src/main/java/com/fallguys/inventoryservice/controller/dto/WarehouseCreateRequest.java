package com.fallguys.inventoryservice.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 창고 등록 요청. 형식·필수 검증은 @Valid로, type 값 검증은 Command가, 유형↔branchId 정합은 도메인이 수행한다.
 * 문자열은 compact constructor에서 trim하며, 주소는 빈 값이면 null로 정규화한다(선택 입력).
 *
 * @param code     창고 코드(필수, 시스템 유일은 도메인이 검증)
 * @param name     창고명(필수)
 * @param type     창고 유형 문자열(필수, HQ/DEALER — 값 검증은 Command)
 * @param branchId 소속 지점 PK(DEALER 필수·HQ 불가는 도메인이 검증)
 * @param address  주소(선택)
 */
public record WarehouseCreateRequest(

        @NotBlank(message = "창고 코드는 필수입니다.")
        @Size(max = 50, message = "창고 코드는 50자를 초과할 수 없습니다.")
        String code,

        @NotBlank(message = "창고명은 필수입니다.")
        @Size(max = 100, message = "창고명은 100자를 초과할 수 없습니다.")
        String name,

        @NotBlank(message = "창고 유형은 필수입니다.")
        String type,

        Long branchId,

        @Size(max = 255, message = "주소는 255자를 초과할 수 없습니다.")
        String address
) {

    public WarehouseCreateRequest {
        code = code == null ? null : code.trim();
        name = name == null ? null : name.trim();
        type = type == null ? null : type.trim();
        address = (address == null || address.isBlank()) ? null : address.trim();
    }
}
