package com.fallguys.inventoryservice.domain.exception;

import lombok.Getter;

@Getter
public enum InventoryErrorCode {

    WAREHOUSE_NOT_FOUND("INV-001", "창고를 찾을 수 없습니다."),
    BRANCH_LOCATION_NOT_FOUND("INV-002", "지점을 찾을 수 없습니다."),
    INVALID_PARAMETER("INV-003", "요청 파라미터가 올바르지 않습니다."),
    INTERNAL_ERROR("INV-999", "서버 내부 오류가 발생했습니다.");

    private final String code;
    private final String defaultMessage;

    InventoryErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

}
