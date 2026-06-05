package com.fallguys.inventoryservice.branchlocation.domain.exception;

import lombok.Getter;

/**
 * 소속 지점 애그리거트 에러 코드. code 값은 API 명세의 errorCode 계약을 그대로 따른다.
 */
@Getter
public enum BranchLocationErrorCode {

    BRANCH_LOCATION_NAME_DUPLICATE("BRANCH_LOCATION_NAME_DUPLICATE", "이미 존재하는 지점명입니다.");

    private final String code;
    private final String defaultMessage;

    BranchLocationErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
