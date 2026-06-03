package com.fallguys.inventoryservice.domain.query;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 정렬 가능한 창고 속성 화이트리스트. property는 API 파라미터 이름이자 영속 엔티티의 속성 이름이다.
 */
public enum WarehouseSortField {
    CODE("code"),
    NAME("name"),
    TYPE("type"),
    CREATED_AT("createdAt");

    private final String property;

    WarehouseSortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }

    public static Optional<WarehouseSortField> fromProperty(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (WarehouseSortField field : values()) {
            if (field.property.equalsIgnoreCase(raw.trim())) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    public static List<String> allowedProperties() {
        return Arrays.stream(values()).map(WarehouseSortField::property).toList();
    }
}
