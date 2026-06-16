package com.fallguys.inventoryservice.stock.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.exception.ParameterViolation;
import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.shared.security.JwtClaimExtractor;
import com.fallguys.inventoryservice.stock.controller.dto.ItemStockListResponse;
import com.fallguys.inventoryservice.stock.domain.StockService;
import com.fallguys.inventoryservice.stock.domain.query.ItemStockRow;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inventory/items")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "재고 API")
public class ItemStockController {

    private final StockService stockService;

    /**
     * 부품 마스터 화면에서 선택한 sku의 창고별 현재고를 최근 수정 순 최대 5건 조회한다. 전 Role 호출 가능하나 Tenancy로 범위가 차등된다.
     * ADMIN·HQ는 warehouseCode가 있으면 해당 창고만, 없으면 전사 창고를 조회한다. BRANCH는 자기 창고만 조회되며 타 창고 코드는 403.
     * 재고 행이 없어도 404가 아니라 빈 stocks[]로 200을 반환한다(비활성 부품도 조회, 비활성 창고는 제외).
     * sku 형식 오류(빈 값·'-' 미포함)·warehouseCode 빈 값은 400(INVALID_PARAMETER), ADMIN·HQ가 지정한 미존재 창고는 404(WAREHOUSE_NOT_FOUND).
     */
    @Operation(
            summary = "부품 창고별 현재고 조회(sku)",
            description = "부품 마스터 화면에서 부품 선택 시 창고별 현재고·안전재고·상태를 최근 수정 순 최대 5건 반환한다. "
                    + "ADMIN·HQ는 전체/선택 창고, BRANCH는 자기 지점 창고만 조회된다(타 창고는 403)."
    )
    @GetMapping("/{sku}/stocks")
    public ResponseEntity<ItemStockListResponse> itemStocks(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "부품 코드 (예: HMC-EN-00214)")
            @PathVariable String sku,
            @Parameter(description = "창고 코드. 본사 권한은 선택 조회용, 지점 권한은 본인 창고만 허용(미지정 시 ADMIN·HQ는 전사, BRANCH는 자기 창고)")
            @RequestParam(required = false) String warehouseCode) {
        requireValidParams(sku, warehouseCode);
        TenancyType tenancyType = JwtClaimExtractor.extractTenancyType(jwt);
        // BRANCH만 자기 창고로 한정하므로 그때만 tenancy_code를 요구한다(ADMIN/HQ는 전사라 불필요).
        String tenancyCode = tenancyType == TenancyType.BRANCH ? JwtClaimExtractor.extractTenancyCode(jwt) : null;
        List<ItemStockRow> rows = stockService.getItemStocks(sku, warehouseCode, tenancyType, tenancyCode);
        return ResponseEntity.ok(ItemStockListResponse.from(sku, rows));
    }

    /**
     * path·query 파라미터 형식을 검증한다. 위반 시 400(INVALID_PARAMETER, details[]에 위반 필드).
     * sku는 빈 값·'-' 미포함이면 위반(기존 sku 검증과 동일), warehouseCode는 지정됐는데 빈 문자열이면 위반(미지정은 허용).
     */
    private static void requireValidParams(String sku, String warehouseCode) {
        List<ParameterViolation> violations = new ArrayList<>();
        if (sku == null || sku.isBlank() || !sku.contains("-")) {
            violations.add(new ParameterViolation("sku", sku, List.of("'-' 포함 코드")));
        }
        if (warehouseCode != null && warehouseCode.isBlank()) {
            violations.add(new ParameterViolation("warehouseCode", warehouseCode, List.of()));
        }
        if (!violations.isEmpty()) {
            throw new InvalidParameterException(violations);
        }
    }
}
