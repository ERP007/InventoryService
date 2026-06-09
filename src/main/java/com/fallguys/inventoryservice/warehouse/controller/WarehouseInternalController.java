package com.fallguys.inventoryservice.warehouse.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.warehouse.controller.dto.WarehouseInfoResponse;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseService;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 서비스 간 내부 호출용 창고 API. 사설망 전용이며 게이트웨이가 외부 노출을 차단한다.
 * 인증(JWT)은 리소스 서버가 강제하되 사용자 Role 게이팅은 하지 않는다(내부 서비스 호출).
 */
@RestController
@RequestMapping("/internal/inventory/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouse Internal", description = "창고 내부 연계 API")
public class WarehouseInternalController {

    private final WarehouseService warehouseService;

    /**
     * 창고 코드로 창고 기본 정보(존재·활성·유형·소속)를 조회한다.
     * Procurement·Sales가 입출고 직전 대상 창고를 재검증할 때 사용한다(Client단 창고 코드 변조 대비).
     * 없으면 404(WAREHOUSE_NOT_FOUND) — 내부 호출이라 존재 은닉 없이 단순 "없음"이다.
     */
    @Operation(
            summary = "창고 검증 조회(내부)",
            description = "PO/SO 입출고 직전 대상 창고의 존재·활성·유형·소속을 재검증하기 위한 내부 조회. 표시용 필드는 최소화한다."
    )
    @GetMapping("/{code}")
    public ResponseEntity<WarehouseInfoResponse> getByCode(
            @Parameter(description = "창고 코드 (예: WH-SE-001)")
            @PathVariable String code) {
        WarehouseSummaryForEdit warehouse = warehouseService.getByCode(code);
        return ResponseEntity.ok(WarehouseInfoResponse.from(warehouse));
    }
}
