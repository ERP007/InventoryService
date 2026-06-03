package com.fallguys.inventoryservice.domain.query;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum SortDirection {
    ASC,
    DESC;

    public static Optional<SortDirection> fromString(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (SortDirection direction : values()) {
            if (direction.name().equalsIgnoreCase(raw.trim())) {
                return Optional.of(direction);
            }
        }
        return Optional.empty();
    }

    public static List<String> allowed() {
        return List.of("asc", "desc");
    }

    public String paramValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
