package com.fallguys.inventoryservice.stock.domain.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.exception.ParameterViolation;
import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.stock.domain.StockStatus;

/**
 * 재고 목록 조회 조건. 모든 필드는 검증을 통과한 값이다.
 *
 * @param keyword        부품명/SKU 부분 일치 검색어(없으면 null)
 * @param warehouseCodes 창고 코드 다중 필터(없으면 빈 리스트 = 전체)
 * @param status         재고 상태 필터(없으면 null = 전체)
 * @param sortField      정렬 속성
 * @param sortDirection  정렬 방향
 * @param page           페이지(1-base)
 * @param size           페이지 크기(20/50/100)
 */
public record StockSearchQuery(
        String keyword,
        List<String> warehouseCodes,
        StockStatus status,
        StockSortField sortField,
        SortDirection sortDirection,
        int page,
        int size
) {

    private static final StockSortField DEFAULT_FIELD = StockSortField.NAME;
    private static final SortDirection DEFAULT_DIRECTION = SortDirection.ASC;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final List<Integer> ALLOWED_SIZES = List.of(20, 50, 100);

    public StockSearchQuery {
        warehouseCodes = warehouseCodes == null ? List.of() : List.copyOf(warehouseCodes);
    }

    /**
     * 원시 쿼리 파라미터를 검증하여 조회 조건을 만든다.
     * keyword·warehouseCodes는 정규화만 하고, status/sort/page/size는 화이트리스트로 검증해 위반을 모두 모은다.
     *
     * @throws InvalidParameterException status/sort/page/size 중 허용치 밖 값이 있을 때(400, 모든 위반 누적)
     */
    public static StockSearchQuery of(String keyword, String warehouseCodesCsv, String status,
                                      String sort, Integer page, Integer size) {
        List<ParameterViolation> violations = new ArrayList<>();

        String normalizedKeyword = normalizeKeyword(keyword);
        List<String> parsedWarehouseCodes = parseWarehouseCodes(warehouseCodesCsv);
        StockStatus parsedStatus = parseStatus(status, violations);

        StockSortField sortField = DEFAULT_FIELD;
        SortDirection sortDirection = DEFAULT_DIRECTION;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            if (parts.length != 2) {
                violations.add(new ParameterViolation("sort", sort, List.of("{property},{direction}")));
            } else {
                Optional<StockSortField> field = StockSortField.fromParam(parts[0]);
                Optional<SortDirection> direction = SortDirection.fromString(parts[1]);
                if (field.isEmpty()) {
                    violations.add(new ParameterViolation("sort", sort, StockSortField.allowedParams()));
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
        return new StockSearchQuery(
                normalizedKeyword, parsedWarehouseCodes, parsedStatus, sortField, sortDirection, parsedPage, parsedSize);
    }

    /** BRANCH 조회 범위 강제용: 창고 필터를 사용자의 단일 창고로 교체한 사본을 만든다. */
    public StockSearchQuery withWarehouseCodes(List<String> codes) {
        return new StockSearchQuery(keyword, codes, status, sortField, sortDirection, page, size);
    }

    /** 창고 코드 필터가 있는지 여부(없으면 전체 창고). */
    public boolean hasWarehouseFilter() {
        return !warehouseCodes.isEmpty();
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

    private static StockStatus parseStatus(String status, List<ParameterViolation> violations) {
        if (status == null || status.isBlank()) {
            return null;
        }
        for (StockStatus candidate : StockStatus.values()) {
            if (candidate.name().equalsIgnoreCase(status.trim())) {
                return candidate;
            }
        }
        violations.add(new ParameterViolation("status", status, List.of("NORMAL", "LOW")));
        return null;
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
