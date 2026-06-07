package com.fallguys.inventoryservice.stock.domain.query;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 정렬 가능한 이동 이력 속성 화이트리스트. param은 API 파라미터 이름이다.
 * 실제 정렬식(엔티티 속성)으로의 변환은 infrastructure가 담당한다.
 */
public enum MovementSortField {
    OCCURRED_AT("occurredAt"),
    DELTA("delta");

    private final String param;

    MovementSortField(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    public static Optional<MovementSortField> fromParam(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (MovementSortField field : values()) {
            if (field.param.equalsIgnoreCase(raw.trim())) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    public static List<String> allowedParams() {
        return Arrays.stream(values()).map(MovementSortField::param).toList();
    }
}
