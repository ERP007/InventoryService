package com.fallguys.inventoryservice.shared.exception;

import java.util.List;

import lombok.Getter;

/**
 * 조회 파라미터(type/status/sort)가 허용 화이트리스트 밖일 때 발생한다. 400으로 매핑된다.
 * 위반 항목들을 details로 보유하여 클라이언트가 어떤 값이 잘못됐는지 알 수 있게 한다.
 */
@Getter
public class InvalidParameterException extends BusinessException {

    private final List<ParameterViolation> details;

    public InvalidParameterException(List<ParameterViolation> details) {
        super(CommonErrorCode.INVALID_PARAMETER.getCode(), CommonErrorCode.INVALID_PARAMETER.getDefaultMessage());
        this.details = List.copyOf(details);
    }
}
