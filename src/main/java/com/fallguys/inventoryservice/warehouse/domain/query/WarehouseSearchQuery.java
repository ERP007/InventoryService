package com.fallguys.inventoryservice.warehouse.domain.query;

import com.fallguys.inventoryservice.shared.query.SortDirection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.exception.ParameterViolation;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;

/**
 * 창고 목록 조회 조건. 모든 필드는 검증을 통과한 값이다.
 *
 * @param keyword 창고명/코드 부분 일치 검색어(없으면 null)
 * @param type    유형 필터(없으면 null = 전체)
 * @param status  활성 상태 필터(기본 ALL)
 * @param sort    정렬 조건(기본 code,asc)
 */
public record WarehouseSearchQuery(
        String keyword,
        WarehouseType type,
        StatusFilter status,
        WarehouseSort sort
) {

    private static final WarehouseSort DEFAULT_SORT =
            new WarehouseSort(WarehouseSortField.CODE, SortDirection.ASC);

    /**
     * 원시 쿼리 파라미터를 검증하여 조회 조건을 만든다.
     *
     * 흐름:
     * 1) keyword는 trim 후 빈 값이면 null로 정규화한다(검증 없음, 모든 문자열 허용).
     * 2) type/status/sort는 화이트리스트로 검증하며 위반을 모두 모은다.
     * 3) 위반이 하나라도 있으면 InvalidParameterException(400)으로 한 번에 보고한다.
     *
     * @throws InvalidParameterException type/status/sort 중 허용치 밖 값이 있을 때(400, 모든 위반 누적)
     */
    public static WarehouseSearchQuery of(String keyword, String type, String status, String sort) {
        List<ParameterViolation> violations = new ArrayList<>();

        String normalizedKeyword = normalizeKeyword(keyword);
        WarehouseType parsedType = parseType(type, violations); // 잘못된 값이 오면 violations 에 추가한다
        StatusFilter parsedStatus = parseStatus(status, violations);
        WarehouseSort parsedSort = parseSort(sort, violations);

        // 올바르지 않은 param 존재하면 throw
        if (!violations.isEmpty()) {
            throw new InvalidParameterException(violations);
        }
        return new WarehouseSearchQuery(normalizedKeyword, parsedType, parsedStatus, parsedSort);
    }

    // 공백 처리
    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // 공백과 enum 값에 해당하는지 체크
    private static WarehouseType parseType(String type, List<ParameterViolation> violations) {
        if (type == null || type.isBlank()) {
            return null;
        }
        for (WarehouseType candidate : WarehouseType.values()) {
            if (candidate.name().equalsIgnoreCase(type.trim())) {
                return candidate;
            }
        }
        violations.add(new ParameterViolation("type", type, List.of("HQ", "DEALER")));
        return null;
    }

    // 공백과 enum 값에 해당하는지 체크
    private static StatusFilter parseStatus(String status, List<ParameterViolation> violations) {
        if (status == null || status.isBlank()) {
            return StatusFilter.ALL;
        }
        Optional<StatusFilter> parsed = StatusFilter.fromString(status);
        if (parsed.isEmpty()) {
            violations.add(new ParameterViolation("status", status, StatusFilter.allowed()));
            return StatusFilter.ALL;
        }
        return parsed.get();
    }

    // 공백과 (필터필드, 방향) 값이 올바른지 체크
    private static WarehouseSort parseSort(String sort, List<ParameterViolation> violations) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String[] parts = sort.split(",");
        if (parts.length != 2) {
            violations.add(new ParameterViolation("sort", sort, List.of("{property},{direction}")));
            return DEFAULT_SORT;
        }

        Optional<WarehouseSortField> field = WarehouseSortField.fromProperty(parts[0]);
        Optional<SortDirection> direction = SortDirection.fromString(parts[1]);

        if (field.isEmpty()) {
            violations.add(new ParameterViolation("sort", sort, WarehouseSortField.allowedProperties()));
        }
        if (direction.isEmpty()) {
            violations.add(new ParameterViolation("sort", sort, SortDirection.allowed()));
        }
        if (field.isEmpty() || direction.isEmpty()) {
            return DEFAULT_SORT;
        }
        return new WarehouseSort(field.get(), direction.get());
    }
}
