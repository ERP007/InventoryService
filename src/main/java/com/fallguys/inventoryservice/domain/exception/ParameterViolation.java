package com.fallguys.inventoryservice.domain.exception;

import java.util.List;

/**
 * 잘못된 요청 파라미터 1건의 상세. ProblemDetail의 details[] 항목으로 직렬화된다.
 *
 * @param field   문제가 된 파라미터 이름 (type / status / sort)
 * @param value   클라이언트가 보낸 값
 * @param allowed 허용되는 값 목록(화이트리스트)
 */
public record ParameterViolation(String field, String value, List<String> allowed) {

    public ParameterViolation {
        allowed = allowed == null ? List.of() : List.copyOf(allowed);
    }
}
