package com.fallguys.inventoryservice.warehouse.domain.query;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 활성 상태 조회 필터. ALL은 활성/비활성 모두 포함한다.
 */
public enum StatusFilter {
    ACTIVE,
    INACTIVE,
    ALL;

    public static Optional<StatusFilter> fromString(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (StatusFilter filter : values()) {
            if (filter.name().equalsIgnoreCase(raw.trim())) {
                return Optional.of(filter);
            }
        }
        return Optional.empty();
    }

    public static List<String> allowed() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }

    /**
     * 영속성 조회용 active 필터 값으로 변환한다.
     *
     * @return ACTIVE → true, INACTIVE → false, ALL → null(필터 없음)
     */
    public Boolean toActiveFilter() {
        return switch (this) {
            case ACTIVE -> Boolean.TRUE;
            case INACTIVE -> Boolean.FALSE;
            case ALL -> null;
        };
    }
}
