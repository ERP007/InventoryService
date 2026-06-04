package com.fallguys.inventoryservice.warehouse.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.warehouse.controller.dto.WarehouseCreateRequest;
import com.fallguys.inventoryservice.warehouse.controller.dto.WarehouseListResponse;
import com.fallguys.inventoryservice.warehouse.controller.dto.WarehouseResponse;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseService;
import com.fallguys.inventoryservice.warehouse.domain.command.CreateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inventory/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouse", description = "창고 API")
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

    /**
     * 신규 창고를 등록한다. ADMIN·HQ_MANAGER만 호출 가능(인가는 게이트웨이가 판단).
     * 형식·필수·type 값 오류는 400(INVALID_PARAMETER), 유형↔branchId 정합은 400(WAREHOUSE_BRANCH_RULE),
     * 소속 지점 미존재는 400(BRANCH_NOT_FOUND), 코드 중복은 409(WAREHOUSE_CODE_DUPLICATE)로 매핑된다.
     */
    @Operation(
            summary = "창고 등록",
            description = "창고 추가 모달의 입력으로 신규 창고를 등록한다. active=true로 생성되며 code는 시스템 유일, "
                    + "DEALER는 branchId 필수·HQ는 branchId 불가."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse create(@Valid @RequestBody WarehouseCreateRequest request) {
        CreateWarehouseCommand command = CreateWarehouseCommand.of(
                request.code(), request.name(), request.type(), request.branchId(), request.address());
        WarehouseSummary summary = warehouseService.create(command);
        return WarehouseResponse.from(summary);
    }
}
