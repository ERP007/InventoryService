package com.fallguys.inventoryservice.shared.web;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.shared.exception.ConflictException;
import com.fallguys.inventoryservice.shared.exception.ForbiddenException;
import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.exception.CommonErrorCode;
import com.fallguys.inventoryservice.shared.exception.ParameterViolation;
import com.fallguys.inventoryservice.shared.exception.ResourceNotFoundException;
import com.fallguys.inventoryservice.shared.exception.ServiceUnavailableException;

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

    /** 리소스 충돌(중복 등): 409. 비즈니스 예외이므로 WARN. BusinessException보다 구체적이라 이 핸들러가 우선한다. */
    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        log.warn("Conflict [{}]: {}", ex.getCode(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    /** 내부 API 호출 실패(§10): 502(BAD_GATEWAY). 원인은 로그로만 보존하고 클라이언트엔 일반 메시지를 준다. 시스템성 실패라 ERROR. */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ProblemDetail handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("Upstream API call failed [{}]: {}", ex.getCode(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_GATEWAY, ex.getCode(),
                "내부 서비스 호출에 실패했습니다. 잠시 후 다시 시도하세요.");
    }

    /** 동시 수정으로 인한 낙관락 충돌(JPA @Version): 409. 최신 재조회 후 재시도를 유도한다. 비즈니스성 충돌이라 WARN. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict [{}]", CommonErrorCode.OPTIMISTIC_LOCK_CONFLICT.getCode());
        return build(HttpStatus.CONFLICT,
                CommonErrorCode.OPTIMISTIC_LOCK_CONFLICT.getCode(),
                CommonErrorCode.OPTIMISTIC_LOCK_CONFLICT.getDefaultMessage());
    }

    /**
     * 비관락 획득 실패(잠금 대기 초과·경합): 409. 동시 출고 직렬화 중 잠금을 시간 내 얻지 못하면 발생하며 재시도를 유도한다.
     * CannotAcquireLockException 등 하위 타입을 포함한다. 비즈니스성 충돌이라 WARN.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail handleLockTimeout(PessimisticLockingFailureException ex) {
        log.warn("Pessimistic lock timeout [{}]", CommonErrorCode.LOCK_TIMEOUT.getCode());
        return build(HttpStatus.CONFLICT,
                CommonErrorCode.LOCK_TIMEOUT.getCode(),
                CommonErrorCode.LOCK_TIMEOUT.getDefaultMessage());
    }

    /** 리소스 없음(존재 은닉 포함): 404. 비즈니스 예외이므로 WARN. BusinessException보다 구체적이라 이 핸들러가 우선한다. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        log.warn("Not found [{}]: {}", ex.getCode(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getMessage());
    }

    /** 인가 실패(권한 없음): 403. 비즈니스 예외이므로 WARN. BusinessException보다 구체적이라 이 핸들러가 우선한다. */
    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden [{}]: {}", ex.getCode(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage());
    }

    /** 그 외 도메인 비즈니스 예외: 400. WARN. */
    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        log.warn("Business error [{}]: {}", ex.getCode(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    /**
     * 요청 바디 형식 검증 실패(@Valid): 400. errorCode를 INVALID_PARAMETER로 통일하고
     * 위반 필드들을 details[]로 노출한다(조회 파라미터 검증과 동일한 응답 형태). 비즈니스 외 형식 오류라 WARN.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ParameterViolation> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ParameterViolation(
                        fieldError.getField(),
                        Objects.toString(fieldError.getRejectedValue(), null),
                        List.of()))
                .toList();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(org.springframework.validation.FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(CommonErrorCode.INVALID_PARAMETER.getDefaultMessage());

        log.warn("Invalid request body [{}]: {}", CommonErrorCode.INVALID_PARAMETER.getCode(), details);
        ProblemDetail problemDetail = build(HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_PARAMETER.getCode(), message);
        problemDetail.setProperty("details", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * 경로/파라미터 타입 변환 실패(예: id가 숫자가 아님): 400. errorCode를 INVALID_PARAMETER로 통일한다.
     * 위반 파라미터를 details[]로 노출한다. 형식 오류라 WARN.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String field = (ex instanceof MethodArgumentTypeMismatchException mismatch)
                ? mismatch.getName()
                : ex.getPropertyName();
        ParameterViolation violation = new ParameterViolation(field, Objects.toString(ex.getValue(), null), List.of());

        log.warn("Type mismatch [{}]: field={} value={}",
                CommonErrorCode.INVALID_PARAMETER.getCode(), field, ex.getValue());
        ProblemDetail problemDetail = build(HttpStatus.BAD_REQUEST,
                CommonErrorCode.INVALID_PARAMETER.getCode(), CommonErrorCode.INVALID_PARAMETER.getDefaultMessage());
        problemDetail.setProperty("details", List.of(violation));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * 요청 바디 파싱 실패(JSON 문법 오류·타입 불일치 등): 400. errorCode를 INVALID_PARAMETER로 통일한다.
     * 원본 파싱 상세는 클라이언트에 노출하지 않는다. 형식 오류라 WARN.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        log.warn("Malformed request body [{}]: {}", CommonErrorCode.INVALID_PARAMETER.getCode(), ex.getMessage());
        ProblemDetail problemDetail = build(HttpStatus.BAD_REQUEST,
                CommonErrorCode.INVALID_PARAMETER.getCode(), CommonErrorCode.INVALID_PARAMETER.getDefaultMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /** 예기치 못한 시스템 예외: 500. 스택트레이스를 남기기 위해 ERROR. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                CommonErrorCode.INTERNAL_ERROR.getCode(),
                CommonErrorCode.INTERNAL_ERROR.getDefaultMessage()
        );
    }

    private ProblemDetail build(HttpStatus status, String errorCode, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setProperty("errorCode", errorCode);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        return problemDetail;
    }
}
