package com.fallguys.inventoryservice.stock.domain.query;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 정렬 가능한 재고 속성 화이트리스트. param은 API 파라미터 이름이다.
 * 실제 정렬식(엔티티 속성·계산식)으로의 변환은 infrastructure가 담당한다(safetyRatio는 계산식).
 */
public enum StockSortField {
    NAME("name"),
    QUANTITY("quantity"),
    SAFETY_RATIO("safetyRatio"),
    LAST_ADJUSTED_AT("lastAdjustedAt");

    private final String param;

    StockSortField(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    public static Optional<StockSortField> fromParam(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (StockSortField field : values()) {
            if (field.param.equalsIgnoreCase(raw.trim())) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    public static List<String> allowedParams() {
        return Arrays.stream(values()).map(StockSortField::param).toList();
    }
}
