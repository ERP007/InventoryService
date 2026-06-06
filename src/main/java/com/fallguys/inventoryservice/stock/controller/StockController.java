package com.fallguys.inventoryservice.stock.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.shared.model.UserRole;
import com.fallguys.inventoryservice.shared.security.JwtClaimExtractor;
import com.fallguys.inventoryservice.stock.controller.dto.StockCreateRequest;
import com.fallguys.inventoryservice.stock.controller.dto.StockCreateResponse;
import com.fallguys.inventoryservice.stock.domain.StockService;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inventory/stocks")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "재고 API")
public class StockController {

    private final StockService stockService;

    /**
     * (sku × warehouse) 재고 행을 신규 생성한다. ADMIN 전용(그 외 Role은 403 FORBIDDEN).
     * 값 검증 실패는 400(INVALID_PARAMETER), 창고 미존재는 404(WAREHOUSE_NOT_FOUND),
     * 재고 중복은 409(STOCK_ALREADY_EXISTS)로 매핑된다.
     */
    @Operation(
            summary = "재고 신규 생성(ADMIN)",
            description = "입출고 흐름 밖에서 (sku × warehouse) 재고 행을 직접 생성한다. 초기 데이터 적재·개발 검증용. "
                    + "현재는 Item 마스터 검증 없이 입력값을 그대로 저장한다."
    )
    @PostMapping
    public ResponseEntity<StockCreateResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StockCreateRequest request) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN);
        StockCreateResult result = stockService.create(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(StockCreateResponse.from(result));
    }
}
