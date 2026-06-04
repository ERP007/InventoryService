package com.fallguys.inventoryservice.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 지점 등록 요청. name 하나만 받으며, 형식 검증(@Valid)은 컨트롤러 진입 시 수행된다.
 * 문자열 정규화(trim)는 이 record의 compact constructor에서 처리하여 "공백만 입력"도 빈 값으로 잡는다.
 *
 * @param name 지점명(필수, trim 후 1~100자, 시스템 내 유일은 도메인이 검증)
 */
public record BranchLocationCreateRequest(

        @NotBlank(message = "지점명은 필수입니다.")
        @Size(max = 100, message = "지점명은 100자를 초과할 수 없습니다.")
        String name
) {

    public BranchLocationCreateRequest {
        name = name == null ? null : name.trim();
    }
}
