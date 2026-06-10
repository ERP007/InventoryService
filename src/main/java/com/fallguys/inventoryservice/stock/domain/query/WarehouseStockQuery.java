package com.fallguys.inventoryservice.stock.domain.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.exception.ParameterViolation;

/**
 * (창고 × SKU 집합) 재고 일괄 조회 조건(서비스 간 내부 호출). warehouseCode는 단일, skus는 1~50개다.
 *
 * @param warehouseCode 대상 창고 코드(필수)
 * @param skus          조회할 SKU 목록(정규화·중복 제거된 1~50개)
 */
public record WarehouseStockQuery(
        String warehouseCode,
        List<String> skus
) {

    private static final int MAX_SKUS = 50;

    public WarehouseStockQuery {
        skus = skus == null ? List.of() : List.copyOf(skus);
    }

    /**
     * 원시 쿼리 파라미터를 검증하여 조회 조건을 만든다. warehouseCode 필수, skus는 콤마 구분 1~50개.
     * 위반은 모두 모아 한 번에 던진다.
     *
     * @throws InvalidParameterException warehouseCode 누락·skus 누락/50개 초과 시(400)
     */
    public static WarehouseStockQuery of(String warehouseCode, String skusCsv) {
        List<ParameterViolation> violations = new ArrayList<>();

        String normalizedWarehouseCode = warehouseCode == null ? null : warehouseCode.trim();
        if (normalizedWarehouseCode == null || normalizedWarehouseCode.isEmpty()) {
            violations.add(new ParameterViolation("warehouseCode", warehouseCode, List.of("필수")));
        }

        List<String> parsedSkus = parseSkus(skusCsv);
        if (parsedSkus.isEmpty()) {
            violations.add(new ParameterViolation("skus", skusCsv, List.of("1개 이상")));
        } else if (parsedSkus.size() > MAX_SKUS) {
            violations.add(new ParameterViolation(
                    "skus", String.valueOf(parsedSkus.size()), List.of("최대 " + MAX_SKUS + "개")));
        }

        if (!violations.isEmpty()) {
            throw new InvalidParameterException(violations);
        }
        return new WarehouseStockQuery(normalizedWarehouseCode, parsedSkus);
    }

    /** 콤마 구분 문자열을 정규화(trim·공백 제거·중복 제거)한 SKU 목록으로 변환한다. 없으면 빈 리스트. */
    private static List<String> parseSkus(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}
