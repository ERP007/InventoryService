package com.fallguys.inventoryservice.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.fallguys.inventoryservice.shared.exception.BusinessException;
import com.fallguys.inventoryservice.shared.exception.ForbiddenException;
import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.exception.ParameterViolation;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 잘못된_파라미터는_400과_errorCode_timestamp_details를_채운다() {
        InvalidParameterException ex = new InvalidParameterException(
                List.of(new ParameterViolation("type", "FACTORY", List.of("HQ", "DEALER"))));

        ProblemDetail problemDetail = handler.handleInvalidParameter(ex);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getProperties()).containsKey("timestamp");
        assertThat(problemDetail.getProperties().get("errorCode")).isEqualTo("INVALID_PARAMETER");
        assertThat(problemDetail.getProperties().get("details")).isEqualTo(ex.getDetails());
    }

    @Test
    void 일반_비즈니스_예외는_400으로_매핑된다() {
        BusinessException ex = new BusinessException("INV-XYZ", "도메인 규칙 위반") {
        };

        ProblemDetail problemDetail = handler.handleBusiness(ex);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getProperties().get("errorCode")).isEqualTo("INV-XYZ");
        assertThat(problemDetail.getDetail()).isEqualTo("도메인 규칙 위반");
    }

    @Test
    void 인가실패는_403과_FORBIDDEN으로_매핑된다() {
        ProblemDetail problemDetail = handler.handleForbidden(new ForbiddenException());

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problemDetail.getProperties().get("errorCode")).isEqualTo("FORBIDDEN");
        assertThat(problemDetail.getProperties()).containsKey("timestamp");
    }

    @Test
    void 예기치못한_예외는_500과_INTERNAL_ERROR로_매핑된다() {
        ProblemDetail problemDetail = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problemDetail.getProperties().get("errorCode")).isEqualTo("INTERNAL_ERROR");
    }
}
