package com.fallguys.inventoryservice.stock.domain.exception;

import lombok.Getter;

/**
 * 재고 애그리거트 에러 코드. code 값은 API 명세의 errorCode 계약을 그대로 따른다.
 */
@Getter
public enum StockErrorCode {

    STOCK_NOT_FOUND("STOCK_NOT_FOUND", "재고를 찾을 수 없습니다."),
    STOCK_ALREADY_EXISTS("STOCK_ALREADY_EXISTS", "이미 존재하는 재고입니다. 재고 조정을 사용하세요."),
    NO_STOCK_CHANGE("NO_STOCK_CHANGE", "변동이 없어 조정할 수 없습니다."),
    INSUFFICIENT_STOCK("INSUFFICIENT_STOCK", "가용 재고를 초과해 차감할 수 없습니다.");

    private final String code;
    private final String defaultMessage;

    StockErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
