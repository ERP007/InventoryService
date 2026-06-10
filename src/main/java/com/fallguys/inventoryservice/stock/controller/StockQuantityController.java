package com.fallguys.inventoryservice.stock.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.shared.security.JwtClaimExtractor;
import com.fallguys.inventoryservice.stock.controller.dto.StockQuantityListResponse;
import com.fallguys.inventoryservice.stock.domain.StockService;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.WarehouseStockQuery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inventory/stocks")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "재고 API")
public class StockQuantityController {

    private final StockService stockService;

    /**
     * 지정한 SKU들의 현재고·안전재고를 일괄 조회한다(SO 발주 라인 추가/프리필 시 수량 참고용). 전 Role 호출 가능.
     * BRANCH는 자기 창고(tenancy_code)로 강제되어 요청 warehouseCode는 무시되고, ADMIN·HQ는 요청 warehouseCode를 사용한다.
     * 재고 행이 없는 SKU는 응답에서 생략된다(호출 측이 0으로 간주). skus(최대 50)·warehouseCode 누락은 400, 없는 창고는 404.
     */
    @Operation(
            summary = "재고 일괄 조회(SKU 다건)",
            description = "warehouseCode와 skus(콤마 구분, 최대 50개)로 현재고·안전재고를 일괄 반환한다. "
                    + "BRANCH는 자기 지점 창고로 강제되며, 재고 행이 없는 SKU는 생략된다."
    )
    @GetMapping("/quantities")
    public ResponseEntity<StockQuantityListResponse> quantities(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "창고 코드 (BRANCH는 무시되고 자기 창고로 강제)")
            @RequestParam(required = false) String warehouseCode,
            @Parameter(description = "SKU 목록(콤마 구분, 최대 50개)")
            @RequestParam(required = false) String skus) {
        TenancyType tenancyType = JwtClaimExtractor.extractTenancyType(jwt);
        // BRANCH는 자기 창고로 한정(요청 warehouseCode 무시), ADMIN·HQ는 요청값 그대로 사용.
        String effectiveWarehouseCode = tenancyType == TenancyType.BRANCH
                ? JwtClaimExtractor.extractTenancyCode(jwt) : warehouseCode;
        WarehouseStockQuery query = WarehouseStockQuery.of(effectiveWarehouseCode, skus);
        List<StockQuantity> quantities = stockService.getStockQuantities(query);
        return ResponseEntity.ok(StockQuantityListResponse.from(query.warehouseCode(), quantities));
    }
}
