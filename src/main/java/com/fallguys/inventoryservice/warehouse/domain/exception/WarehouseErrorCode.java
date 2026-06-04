package com.fallguys.inventoryservice.warehouse.domain.exception;

import lombok.Getter;

/**
 * 창고 애그리거트 에러 코드. code 값은 API 명세의 errorCode 계약을 그대로 따른다.
 */
@Getter
public enum WarehouseErrorCode {

    WAREHOUSE_BRANCH_RULE("WAREHOUSE_BRANCH_RULE", "창고 유형과 소속 지점 정합이 맞지 않습니다."),
    BRANCH_NOT_FOUND("BRANCH_NOT_FOUND", "존재하지 않는 소속 지점입니다."),
    WAREHOUSE_CODE_DUPLICATE("WAREHOUSE_CODE_DUPLICATE", "이미 존재하는 창고 코드입니다."),
    WAREHOUSE_NOT_FOUND("WAREHOUSE_NOT_FOUND", "창고를 찾을 수 없습니다."),
    WAREHOUSE_CODE_IMMUTABLE("WAREHOUSE_CODE_IMMUTABLE", "창고 코드는 변경할 수 없습니다.");

    private final String code;
    private final String defaultMessage;

    WarehouseErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
