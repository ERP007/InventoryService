package com.fallguys.inventoryservice.stock.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.stock.controller.dto.InternalStockListResponse;
import com.fallguys.inventoryservice.stock.domain.StockService;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.WarehouseStockQuery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 서비스 간 내부 호출용 재고 API. 사설망 전용이며 게이트웨이가 외부 노출을 차단한다.
 * 인증(JWT)은 리소스 서버가 강제하되 사용자 Role 게이팅은 하지 않는다(내부 서비스 호출).
 */
@RestController
@RequestMapping("/internal/inventory/stocks")
@RequiredArgsConstructor
@Tag(name = "Stock Internal", description = "재고 내부 연계 API")
public class StockInternalController {

    private final StockService stockService;

    /**
     * 한 창고의 지정 SKU들에 대한 현재고·안전재고를 일괄 조회한다. Sales 출고 화면 구성 등에 사용한다.
     * 재고 행이 없는 (sku×창고)는 응답에서 생략된다(호출 측이 0으로 간주). 창고가 없으면 404(WAREHOUSE_NOT_FOUND).
     * 파라미터 누락·skus 50개 초과는 400(INVALID_PARAMETER)로 매핑된다.
     */
    @Operation(
            summary = "재고 일괄 조회(내부)",
            description = "warehouseCode(1개)와 skus(콤마 구분, 최대 50개)로 현재고·안전재고를 일괄 반환한다. "
                    + "재고 행이 없는 SKU는 생략된다."
    )
    @GetMapping
    public ResponseEntity<InternalStockListResponse> getStocks(
            @Parameter(description = "창고 코드 (예: WH-SE-001)")
            @RequestParam(required = false) String warehouseCode,
            @Parameter(description = "SKU 목록(콤마 구분, 최대 50개)")
            @RequestParam(required = false) String skus) {
        WarehouseStockQuery query = WarehouseStockQuery.of(warehouseCode, skus);
        List<StockQuantity> quantities = stockService.getStockQuantities(query);
        return ResponseEntity.ok(InternalStockListResponse.from(query.warehouseCode(), quantities));
    }
}
