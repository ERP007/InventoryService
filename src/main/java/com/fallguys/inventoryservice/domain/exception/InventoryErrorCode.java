package com.fallguys.inventoryservice.domain.exception;

import lombok.Getter;

/**
 * inventory 서비스 에러 코드. code 값은 API 명세의 errorCode 계약을 그대로 따른다.
 */
@Getter
public enum InventoryErrorCode {

    INVALID_PARAMETER("INVALID_PARAMETER", "요청 파라미터가 올바르지 않습니다."),
    BRANCH_LOCATION_NAME_DUPLICATE("BRANCH_LOCATION_NAME_DUPLICATE", "이미 존재하는 지점명입니다."),
    INTERNAL_ERROR("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");

    private final String code;
    private final String defaultMessage;

    InventoryErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
