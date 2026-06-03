package com.fallguys.inventoryservice.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.controller.dto.WarehouseListResponse;
import com.fallguys.inventoryservice.domain.WarehouseService;
import com.fallguys.inventoryservice.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inventory/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouse", description = "창고 조회 API")
public class WarehouseController {

    private final WarehouseService warehouseService;

    /**
     * 창고 목록을 조회한다. 5개 Role 모두 호출 가능하며 응답은 권한과 무관하다.
     * 액션 열 노출 여부는 프론트가 사용자 Role로 판단한다.
     */
    @Operation(
            summary = "창고 목록 조회",
            description = "검색어/유형/상태 필터와 정렬로 창고 목록을 조회한다. 매칭 0건이어도 200과 빈 배열을 반환한다."
    )
    @GetMapping
    public WarehouseListResponse list(
            @Parameter(description = "창고명 또는 창고 코드 부분 일치 (예: 본사 중앙창고, WH-02-001)")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "창고 유형 필터")
            @RequestParam(required = false) String type,
            @Parameter(description = "활성 상태 필터 (기본 ALL)")
            @RequestParam(required = false) String status,
            @Parameter(description = "정렬 (Spring Data 포맷, 기본 code,asc). 속성: code/name/type/createdAt, 방향: asc/desc", example = "code,asc")
            @RequestParam(required = false) String sort
    ) {
        WarehouseSearchQuery query = WarehouseSearchQuery.of(keyword, type, status, sort); // param 이 잘못되면 throw
        List<WarehouseSummary> summaries = warehouseService.search(query);
        return WarehouseListResponse.from(summaries, query.sort().toParam());
    }
}
