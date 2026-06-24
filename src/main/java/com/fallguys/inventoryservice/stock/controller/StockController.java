package com.fallguys.inventoryservice.stock.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.shared.exception.InvalidParameterException;
import com.fallguys.inventoryservice.shared.exception.ParameterViolation;
import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.shared.model.UserRole;
import com.fallguys.inventoryservice.shared.security.JwtClaimExtractor;
import com.fallguys.inventoryservice.stock.controller.dto.SafetyStockEditResponse;
import com.fallguys.inventoryservice.stock.controller.dto.SafetyStockResponse;
import com.fallguys.inventoryservice.stock.controller.dto.SafetyStockUpdateRequest;
import com.fallguys.inventoryservice.stock.controller.dto.StockAdjustmentRequest;
import com.fallguys.inventoryservice.stock.controller.dto.StockAdjustmentResponse;
import com.fallguys.inventoryservice.stock.controller.dto.StockCreateRequest;
import com.fallguys.inventoryservice.stock.controller.dto.StockCreateResponse;
import com.fallguys.inventoryservice.stock.controller.dto.StockDetailResponse;
import com.fallguys.inventoryservice.stock.controller.dto.StockKpiResponse;
import com.fallguys.inventoryservice.stock.controller.dto.StockListResponse;
import com.fallguys.inventoryservice.stock.controller.dto.StockSkuDetailResponse;
import com.fallguys.inventoryservice.stock.domain.StockAdjustmentService;
import com.fallguys.inventoryservice.stock.domain.StockKpiService;
import com.fallguys.inventoryservice.stock.domain.StockService;
import com.fallguys.inventoryservice.stock.domain.StockSkuDetailService;
import com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit;
import com.fallguys.inventoryservice.stock.domain.query.StockAdjustmentResult;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockKpi;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inventory/stocks")
@RequiredArgsConstructor
@Tag(name = "Stock", description = "재고 API")
public class StockController {

    private final StockService stockService;
    private final StockSkuDetailService stockSkuDetailService;
    private final StockKpiService stockKpiService;
    private final StockAdjustmentService stockAdjustmentService;

    /**
     * 재고 목록을 조회한다. 전 Role 호출 가능하나 Tenancy로 범위가 차등된다 — BRANCH는 자기 창고(tenancy_code) 재고만 본다.
     * 검색·창고 다중 필터·상태 필터·정렬·페이지네이션을 지원한다. 매칭 0건이어도 200과 빈 배열을 반환한다.
     * status/sort/page/size가 허용치 밖이면 400(INVALID_PARAMETER)로 매핑된다.
     */
    @Operation(
            summary = "재고 목록 조회",
            description = "부품명/SKU 검색, 창고 다중 필터(콤마 구분), 상태(NORMAL/LOW) 필터, 정렬, 페이지네이션. "
                    + "BRANCH 사용자는 자기 지점 창고 재고만 조회된다."
    )
    @GetMapping
    public ResponseEntity<StockListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "부품명 또는 SKU 부분 일치")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "창고 코드 다중 필터(콤마 구분, 예: WH-SE-001,HQ-001)")
            @RequestParam(required = false) String warehouseCodes,
            @Parameter(description = "재고 상태 필터: NORMAL / LOW")
            @RequestParam(required = false) String status,
            @Parameter(description = "정렬(기본 name,asc). 속성: name/quantity/safetyRatio/lastAdjustedAt, 방향: asc/desc", example = "name,asc")
            @RequestParam(required = false) String sort,
            @Parameter(description = "페이지(1-base, 기본 1)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "페이지 크기(20/50/100 중, 기본 20)")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "비활성(창고/부품) 재고 포함 여부(기본 false = 활성만)")
            @RequestParam(required = false) Boolean includeInactive
    ) {
        StockSearchQuery query = StockSearchQuery.of(keyword, warehouseCodes, status, sort, page, size, includeInactive);
        TenancyType tenancyType = JwtClaimExtractor.extractTenancyType(jwt);
        // BRANCH만 자기 창고로 한정하므로 그때만 tenancy_code를 요구한다(ADMIN/HQ는 전사라 불필요).
        String tenancyCode = tenancyType == TenancyType.BRANCH ? JwtClaimExtractor.extractTenancyCode(jwt) : null;
        StockSummaryPage result = stockService.search(query, tenancyType, tenancyCode);
        return ResponseEntity.ok(StockListResponse.from(result));
    }

    /**
     * (창고 × 부품)의 현재고·안전재고를 조회한다. SO 발주 라인 추가 시 사용하며 BRANCH_* 전용(그 외 Role은 403 FORBIDDEN).
     * 자기 담당 창고(tenancy_code)가 아닌 코드로 호출 시 404(STOCK_NOT_FOUND, 존재 은닉), path가 빈 문자열이면 400.
     * 재고 행이 아직 없으면 quantity=0·safetyStock=0으로 200을 반환한다(빈 stock).
     */
    @Operation(
            summary = "단건 재고 조회(창고×부품)",
            description = "SO 발주 라인 추가용. BRANCH_* 전용이며 자기 담당 창고만 조회 가능하다. "
                    + "재고 행이 없으면 quantity=0·safetyStock=0으로 응답한다."
    )
    @GetMapping("/{warehouseCode}/{sku}")
    public ResponseEntity<StockDetailResponse> detail(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "창고 비즈니스 코드 (예: WH-SE-001)")
            @PathVariable String warehouseCode,
            @Parameter(description = "부품 코드 (예: EO-5W30-1L)")
            @PathVariable String sku
    ) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.BRANCH_MANAGER, UserRole.BRANCH_STAFF);
        requireNotBlank(warehouseCode, sku);
        String tenancyCode = JwtClaimExtractor.extractTenancyCode(jwt);
        StockDetail detail = stockService.getDetail(warehouseCode, sku, tenancyCode);
        return ResponseEntity.ok(StockDetailResponse.from(detail));
    }

    /**
     * sku 상세 패널을 조회한다. 전 Role 호출 가능하나 Tenancy로 범위가 차등된다 — BRANCH는 자기 창고분만 집계된다.
     * 창고별 현재고·안전재고·상태와 전체 합계, 최근 이동 이력 5건을 반환한다.
     * sku 형식 오류(빈 값·'-' 미포함)는 400(INVALID_PARAMETER), 범위 내 재고 없음(소속 외 포함)은 404(STOCK_NOT_FOUND, 존재 은닉).
     */
    @Operation(
            summary = "재고 상세 패널 조회(sku)",
            description = "행 클릭 시 우측 상세 패널 바인딩용. 창고별 재고·상태, 전체 합계, 최근 이동 5건을 반환한다. "
                    + "BRANCH 사용자는 자기 지점 창고분만 집계된다."
    )
    @GetMapping("/{sku}")
    public ResponseEntity<StockSkuDetailResponse> detailBySku(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "부품 코드 (예: HMC-EN-00214)")
            @PathVariable String sku
    ) {
        requireValidSku(sku);
        TenancyType tenancyType = JwtClaimExtractor.extractTenancyType(jwt);
        // BRANCH만 자기 창고로 한정하므로 그때만 tenancy_code를 요구한다(ADMIN/HQ는 전사라 불필요).
        String tenancyCode = tenancyType == TenancyType.BRANCH ? JwtClaimExtractor.extractTenancyCode(jwt) : null;
        StockSkuDetail detail = stockSkuDetailService.getSkuDetail(sku, tenancyType, tenancyCode);
        return ResponseEntity.ok(StockSkuDetailResponse.from(detail));
    }

    /** sku path 변수 형식 검증(빈 값·'-' 미포함 금지). 위반 시 400(INVALID_PARAMETER). */
    private static void requireValidSku(String sku) {
        if (sku == null || sku.isBlank() || !sku.contains("-")) {
            throw new InvalidParameterException(
                    List.of(new ParameterViolation("sku", sku, List.of("'-' 포함 코드"))));
        }
    }

    /** path 변수 형식 검증(빈 문자열 금지). 위반 시 400(INVALID_PARAMETER). */
    private static void requireNotBlank(String warehouseCode, String sku) {
        List<ParameterViolation> violations = new ArrayList<>();
        if (warehouseCode == null || warehouseCode.isBlank()) {
            violations.add(new ParameterViolation("warehouseCode", warehouseCode, List.of()));
        }
        if (sku == null || sku.isBlank()) {
            violations.add(new ParameterViolation("sku", sku, List.of()));
        }
        if (!violations.isEmpty()) {
            throw new InvalidParameterException(violations);
        }
    }

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

    /**
     * 대시보드·재고 화면용 KPI를 조회한다. 전 Role 호출 가능하며 집계 범위는 호출자 소속으로 강제된다(BRANCH는 자기 창고).
     * 총 포지션·부족·안전재고 충족률·최근 7일 이동 건수를 반환한다. 파라미터·body가 없어 검증 실패(400)가 없고 집계 0이어도 200이다.
     */
    @Operation(
            summary = "재고 KPI 조회",
            description = "대시보드·재고 화면 진입/새로고침 시 1회 호출. (sku×창고) 총·부족 포지션 수, 안전재고 충족률, 최근 7일 이동 건수를 반환한다. "
                    + "집계 범위는 호출자 소속으로 강제(BRANCH는 자기 지점 창고)."
    )
    @GetMapping("/kpi")
    public ResponseEntity<StockKpiResponse> kpi(@AuthenticationPrincipal Jwt jwt) {
        TenancyType tenancyType = JwtClaimExtractor.extractTenancyType(jwt);
        // BRANCH만 자기 창고로 한정하므로 그때만 tenancy_code를 요구한다(ADMIN/HQ는 전사라 불필요).
        String tenancyCode = tenancyType == TenancyType.BRANCH ? JwtClaimExtractor.extractTenancyCode(jwt) : null;
        StockKpi kpi = stockKpiService.getKpi(tenancyType, tenancyCode, Instant.now());
        return ResponseEntity.ok(StockKpiResponse.from(kpi));
    }

    /**
     * 재고를 조정하고 이동 이력 1건을 남긴다. ADMIN·HQ_MANAGER 전용(그 외 Role은 403 FORBIDDEN).
     * 형식 오류(reason 누락·증감 ≤0·실사 &lt;0)는 400(INVALID_PARAMETER), 재고 없음은 404(STOCK_NOT_FOUND),
     * 변동 없음은 400(NO_STOCK_CHANGE), 차감 초과는 409(INSUFFICIENT_STOCK)로 매핑된다.
     */
    @Operation(
            summary = "재고 조정(ADMIN·HQ_MANAGER)",
            description = "증가/감소/실사보정으로 재고를 보정하고 append-only 이동 이력을 1건 생성한다. 음수 재고는 거부한다."
    )
    @PostMapping("/adjustments")
    public ResponseEntity<StockAdjustmentResponse> adjust(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StockAdjustmentRequest request) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER);
        String executorEmpNo = JwtClaimExtractor.extractEmployeeNo(jwt);
        String executorName = JwtClaimExtractor.extractName(jwt);
        StockAdjustmentResult result = stockAdjustmentService.adjust(request.toCommand(executorEmpNo, executorName));
        return ResponseEntity.ok(StockAdjustmentResponse.from(result));
    }

    /**
     * 안전재고 조정 모달 프리필을 조회한다. ADMIN·HQ_MANAGER 전용(그 외 Role은 403 FORBIDDEN).
     * (창고 × 부품)의 현재 안전재고·현재고와 후속 수정용 version을 반환한다. 재고 행이 없으면 404(STOCK_NOT_FOUND).
     */
    @Operation(
            summary = "안전재고 조정 프리필 조회(ADMIN·HQ_MANAGER)",
            description = "재고 조회 화면 '안전 재고 조정' 버튼 → 모달 프리필용. (warehouseCode × sku)의 현재 안전재고·현재고·version을 반환한다."
    )
    @GetMapping("/{warehouseCode}/{sku}/safety-stock")
    public ResponseEntity<SafetyStockEditResponse> safetyStockEdit(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "창고 비즈니스 코드 (예: WH-SE-001)")
            @PathVariable String warehouseCode,
            @Parameter(description = "부품 코드 (예: HMC-EN-00214)")
            @PathVariable String sku
    ) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER);
        SafetyStockEdit edit = stockService.getSafetyStockEdit(warehouseCode, sku);
        return ResponseEntity.ok(SafetyStockEditResponse.from(edit));
    }

    /**
     * 안전재고를 수정한다. ADMIN·HQ_MANAGER 전용(그 외 Role은 403 FORBIDDEN).
     * safetyStock을 절대값으로 교체하며 version으로 낙관적 락을 검증한다(수량·이동 이력은 건드리지 않음).
     * 값 오류(누락·음수)는 400(INVALID_PARAMETER), 재고 행 없음은 404(STOCK_NOT_FOUND), version 불일치는 409(OPTIMISTIC_LOCK_CONFLICT).
     */
    @Operation(
            summary = "안전재고 수정(ADMIN·HQ_MANAGER)",
            description = "프리필된 안전재고를 조정한 뒤 최종 저장한다. safetyStock(절대값)·version을 받으며, 동시 수정은 version으로 거부한다."
    )
    @PatchMapping("/{warehouseCode}/{sku}/safety-stock")
    public ResponseEntity<SafetyStockResponse> updateSafetyStock(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "창고 비즈니스 코드 (예: WH-SE-001)")
            @PathVariable String warehouseCode,
            @Parameter(description = "부품 코드 (예: HMC-EN-00214)")
            @PathVariable String sku,
            @Valid @RequestBody SafetyStockUpdateRequest request
    ) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER);
        SafetyStockEdit updated = stockService.updateSafetyStock(request.toCommand(warehouseCode, sku));
        return ResponseEntity.ok(SafetyStockResponse.from(updated));
    }
}
