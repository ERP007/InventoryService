package com.fallguys.inventoryservice.stock.controller;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.shared.security.JwtClaimExtractor;
import com.fallguys.inventoryservice.stock.controller.dto.MovementListResponse;
import com.fallguys.inventoryservice.stock.domain.StockMovementService;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inventory/stocks/movements")
@RequiredArgsConstructor
@Tag(name = "StockMovement", description = "재고 이동 이력 API")
public class StockMovementController {

    // 기간 기본값(최근 30일) 계산 기준 시간대(KST).
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final StockMovementService stockMovementService;

    /**
     * append-only 재고 이동 이력을 조회한다. 전 Role 호출 가능하나 Tenancy로 범위가 차등된다 — BRANCH는 자기 창고 이력만 본다.
     * 검색·창고 다중 필터·유형 필터·기간(기본 최근 30일)·정렬·페이지네이션을 지원한다. 매칭 0건이어도 200과 빈 배열을 반환한다.
     * type/sort/size/page·날짜 형식·기간 역전(from&gt;to)은 400(INVALID_PARAMETER)로 매핑된다.
     */
    @Operation(
            summary = "재고 이동 이력 조회",
            description = "부품명/SKU 검색, 창고 다중 필터(콤마 구분), 유형(INBOUND/OUTBOUND/INCREASE/DECREASE/ADJUST) 필터, "
                    + "기간(기본 최근 30일), 정렬(occurredAt/delta), 페이지네이션. BRANCH 사용자는 자기 지점 창고 이력만 조회된다."
    )
    @GetMapping
    public ResponseEntity<MovementListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "부품명 또는 SKU 부분 일치")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "창고 코드 다중 필터(콤마 구분, 예: WH-SE-001,HQ-001)")
            @RequestParam(required = false) String warehouseCodes,
            @Parameter(description = "이동 유형: INBOUND / OUTBOUND / INCREASE / DECREASE / ADJUST")
            @RequestParam(required = false) String type,
            @Parameter(description = "조회 시작일(ISO, 기본 30일 전)", example = "2026-05-08")
            @RequestParam(required = false) String from,
            @Parameter(description = "조회 종료일(ISO, 기본 오늘)", example = "2026-06-07")
            @RequestParam(required = false) String to,
            @Parameter(description = "정렬(기본 occurredAt,desc). 속성: occurredAt/delta, 방향: asc/desc", example = "occurredAt,desc")
            @RequestParam(required = false) String sort,
            @Parameter(description = "페이지(1-base, 기본 1)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "페이지 크기(20/50/100 중, 기본 20)")
            @RequestParam(required = false) Integer size
    ) {
        MovementSearchQuery query = MovementSearchQuery.of(
                keyword, warehouseCodes, type, from, to, sort, page, size, LocalDate.now(ZONE));
        TenancyType tenancyType = JwtClaimExtractor.extractTenancyType(jwt);
        // BRANCH만 자기 창고로 한정하므로 그때만 tenancy_code를 요구한다(ADMIN/HQ는 전사라 불필요).
        String tenancyCode = tenancyType == TenancyType.BRANCH ? JwtClaimExtractor.extractTenancyCode(jwt) : null;
        MovementSummaryPage result = stockMovementService.search(query, tenancyType, tenancyCode);
        return ResponseEntity.ok(MovementListResponse.from(result));
    }
}
