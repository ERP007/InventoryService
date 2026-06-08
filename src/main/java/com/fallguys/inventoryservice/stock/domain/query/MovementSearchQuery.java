package com.fallguys.inventoryservice.stock.domain.query;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.exception.ParameterViolation;
import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.stock.domain.MovementType;

/**
 * 이동 이력 조회 조건. 모든 필드는 검증을 통과한 값이며 from·to는 기본값이 적용된 실제 조회 구간(일자)이다.
 *
 * @param keyword        부품명/SKU 부분 일치 검색어(없으면 null)
 * @param warehouseCodes 창고 코드 다중 필터(없으면 빈 리스트 = 전체)
 * @param type           이동 유형 필터(없으면 null = 전체)
 * @param from           조회 시작일(미지정 시 today-30)
 * @param to             조회 종료일(미지정 시 today). 구간은 [from 00:00, to+1일 00:00)로 해석(어댑터)
 * @param sortField      정렬 속성
 * @param sortDirection  정렬 방향
 * @param page           페이지(1-base)
 * @param size           페이지 크기(20/50/100)
 */
public record MovementSearchQuery(
        String keyword,
        List<String> warehouseCodes,
        MovementType type,
        LocalDate from,
        LocalDate to,
        MovementSortField sortField,
        SortDirection sortDirection,
        int page,
        int size
) {

    private static final MovementSortField DEFAULT_FIELD = MovementSortField.OCCURRED_AT;
    private static final SortDirection DEFAULT_DIRECTION = SortDirection.DESC;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final List<Integer> ALLOWED_SIZES = List.of(20, 50, 100);

    public MovementSearchQuery {
        warehouseCodes = warehouseCodes == null ? List.of() : List.copyOf(warehouseCodes);
    }

    /**
     * 원시 쿼리 파라미터를 검증하여 조회 조건을 만든다.
     * keyword·warehouseCodes는 정규화만 하고, type/from/to/sort/page/size는 검증해 위반을 모두 모은다.
     * from·to 미지정 시 today 기준 최근 30일로 기본값을 채운다(today는 호출부가 시계에서 주입).
     *
     * @throws InvalidParameterException type/날짜형식/기간역전/sort/page/size 중 위반이 있을 때(400, 모든 위반 누적)
     */
    public static MovementSearchQuery of(String keyword, String warehouseCodesCsv, String type,
                                         String from, String to, String sort,
                                         Integer page, Integer size, LocalDate today) {
        List<ParameterViolation> violations = new ArrayList<>();

        String normalizedKeyword = normalizeKeyword(keyword);
        List<String> parsedWarehouseCodes = parseWarehouseCodes(warehouseCodesCsv);
        MovementType parsedType = parseType(type, violations);

        LocalDate parsedFrom = parseDate("from", from, violations);
        LocalDate parsedTo = parseDate("to", to, violations);
        boolean dateParseFailed = (isPresent(from) && parsedFrom == null) || (isPresent(to) && parsedTo == null);
        LocalDate effectiveFrom = parsedFrom != null ? parsedFrom : today.minusDays(DEFAULT_RANGE_DAYS);
        LocalDate effectiveTo = parsedTo != null ? parsedTo : today;
        if (!dateParseFailed && effectiveFrom.isAfter(effectiveTo)) {
            violations.add(new ParameterViolation("from", effectiveFrom + "~" + effectiveTo, List.of("from <= to")));
        }

        MovementSortField sortField = DEFAULT_FIELD;
        SortDirection sortDirection = DEFAULT_DIRECTION;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (parts.length != 2) {
                violations.add(new ParameterViolation("sort", sort, List.of("{property},{direction}")));
            } else {
                Optional<MovementSortField> field = MovementSortField.fromParam(parts[0]);
                Optional<SortDirection> direction = SortDirection.fromString(parts[1]);
                if (field.isEmpty()) {
                    violations.add(new ParameterViolation("sort", sort, MovementSortField.allowedParams()));
                }
                if (direction.isEmpty()) {
                    violations.add(new ParameterViolation("sort", sort, SortDirection.allowed()));
                }
                if (field.isPresent()) {
                    sortField = field.get();
                }
                if (direction.isPresent()) {
                    sortDirection = direction.get();
                }
            }
        }

        int parsedPage = parsePage(page, violations);
        int parsedSize = parseSize(size, violations);

        if (!violations.isEmpty()) {
            throw new InvalidParameterException(violations);
        }
        return new MovementSearchQuery(normalizedKeyword, parsedWarehouseCodes, parsedType,
                effectiveFrom, effectiveTo, sortField, sortDirection, parsedPage, parsedSize);
    }

    /** BRANCH 조회 범위 강제용: 창고 필터를 사용자의 단일 창고로 교체한 사본을 만든다. */
    public MovementSearchQuery withWarehouseCodes(List<String> codes) {
        return new MovementSearchQuery(keyword, codes, type, from, to, sortField, sortDirection, page, size);
    }

    /** 창고 코드 필터가 있는지 여부(없으면 전체 창고). */
    public boolean hasWarehouseFilter() {
        return !warehouseCodes.isEmpty();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> parseWarehouseCodes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private static MovementType parseType(String type, List<ParameterViolation> violations) {
        if (type == null || type.isBlank()) {
            return null;
        }
        for (MovementType candidate : MovementType.values()) {
            if (candidate.name().equalsIgnoreCase(type.trim())) {
                return candidate;
            }
        }
        violations.add(new ParameterViolation("type", type,
                Arrays.stream(MovementType.values()).map(Enum::name).toList()));
        return null;
    }

    private static LocalDate parseDate(String field, String value, List<ParameterViolation> violations) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException invalid) {
            violations.add(new ParameterViolation(field, value, List.of("ISO-8601 date (YYYY-MM-DD)")));
            return null;
        }
    }

    private static int parsePage(Integer page, List<ParameterViolation> violations) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 1) {
            violations.add(new ParameterViolation("page", String.valueOf(page), List.of(">= 1")));
            return DEFAULT_PAGE;
        }
        return page;
    }

    private static int parseSize(Integer size, List<ParameterViolation> violations) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (!ALLOWED_SIZES.contains(size)) {
            violations.add(new ParameterViolation("size", String.valueOf(size), List.of("20", "50", "100")));
            return DEFAULT_SIZE;
        }
        return size;
    }
}
