package com.fallguys.inventoryservice.controller;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fallguys.inventoryservice.domain.exception.BusinessException;
import com.fallguys.inventoryservice.domain.exception.InvalidParameterException;
import com.fallguys.inventoryservice.domain.exception.InventoryErrorCode;

/**
 * 모든 예외의 HTTP 변환을 전담한다. 예외 로깅도 이 곳에서만 수행한다(중복 금지).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 잘못된 조회 파라미터: 400 + details[]. 비즈니스 예외이므로 WARN. */
    @ExceptionHandler(InvalidParameterException.class)
    public ProblemDetail handleInvalidParameter(InvalidParameterException ex) {
        log.warn("Invalid parameter [{}]: {}", ex.getCode(), ex.getDetails());
        ProblemDetail problemDetail = build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
        problemDetail.setProperty("details", ex.getDetails());
        return problemDetail;
    }

    /** 그 외 도메인 비즈니스 예외: 400. WARN. */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        log.warn("Business error [{}]: {}", ex.getCode(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    /** 예기치 못한 시스템 예외: 500. 스택트레이스를 남기기 위해 ERROR. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                InventoryErrorCode.INTERNAL_ERROR.getCode(),
                InventoryErrorCode.INTERNAL_ERROR.getDefaultMessage()
        );
    }

    private ProblemDetail build(HttpStatus status, String errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("errorCode", errorCode);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        return problemDetail;
    }
}
