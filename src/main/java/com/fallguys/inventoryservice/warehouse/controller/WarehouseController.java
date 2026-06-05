package com.fallguys.inventoryservice.warehouse.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.fallguys.inventoryservice.shared.model.UserRole;
import com.fallguys.inventoryservice.shared.security.JwtClaimExtractor;
import com.fallguys.inventoryservice.warehouse.controller.dto.WarehouseActiveRequest;
import com.fallguys.inventoryservice.warehouse.controller.dto.WarehouseActiveResponse;
import com.fallguys.inventoryservice.warehouse.controller.dto.WarehouseCreateRequest;
import com.fallguys.inventoryservice.warehouse.controller.dto.WarehouseDetailResponse;
import com.fallguys.inventoryservice.warehouse.controller.dto.WarehouseListResponse;
import com.fallguys.inventoryservice.warehouse.controller.dto.WarehouseResponse;
import com.fallguys.inventoryservice.warehouse.controller.dto.WarehouseUpdateRequest;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseService;
import com.fallguys.inventoryservice.warehouse.domain.command.ChangeWarehouseActiveCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.CreateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

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
     * 신규 창고를 등록한다. ADMIN·HQ_MANAGER만 호출 가능(그 외 Role은 403 FORBIDDEN).
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
    public WarehouseResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WarehouseCreateRequest request) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER);
        CreateWarehouseCommand command = CreateWarehouseCommand.of(
                request.code(), request.name(), request.type(), request.branchId(), request.address());
        WarehouseSummary summary = warehouseService.create(command);
        return WarehouseResponse.from(summary);
    }


    /**
     * 창고 단건을 상세 조회한다. 수정 모달 프리필용이며 ADMIN·HQ_MANAGER만 호출 가능(그 외 Role은 403 FORBIDDEN).
     * 창고 코드(code)로 조회하며 version은 후속 수정 호출에 사용된다.
     * 없거나 소속 외면 404(WAREHOUSE_NOT_FOUND, 존재 은닉)로 매핑된다.
     */
    @Operation(
            summary = "창고 단건 조회",
            description = "수정 모달 프리필을 위해 창고 코드로 전체 필드를 조회한다(branchId·address·version 포함)."
    )
    @GetMapping("/{code}")
    public WarehouseDetailResponse detail(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "창고 코드 (예: WH-SE-001)")
            @PathVariable String code
    ) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER);
        WarehouseSummaryForEdit detail = warehouseService.getByCode(code);
        return WarehouseDetailResponse.from(detail);
    }

    /**
     * 창고의 변경 가능 항목을 수정한다. ADMIN·HQ_MANAGER만 호출 가능(그 외 Role은 403 FORBIDDEN).
     * code 포함 시 400(WAREHOUSE_CODE_IMMUTABLE), 값/정합 오류는 400, 없으면 404(WAREHOUSE_NOT_FOUND),
     * version 불일치는 409(OPTIMISTIC_LOCK_CONFLICT)로 매핑된다.
     */
    @Operation(
            summary = "창고 수정",
            description = "창고명·유형·소속 지점·주소를 수정한다. code는 변경 불가이며 version으로 낙관적 락을 검증한다."
    )
    @PutMapping("/{id}")
    public WarehouseDetailResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "창고 내부 PK")
            @PathVariable Long id,
            @Valid @RequestBody WarehouseUpdateRequest request
    ) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER);
        UpdateWarehouseCommand command = UpdateWarehouseCommand.of(
                request.code(), request.name(), request.type(),
                request.branchId(), request.address(), request.version());
        WarehouseSummaryForEdit detail = warehouseService.update(id, command);
        return WarehouseDetailResponse.from(detail);
    }

    /**
     * 창고 활성 상태를 전환한다. ADMIN·HQ_MANAGER만 호출 가능(그 외 Role은 403 FORBIDDEN).
     * 같은 값이면 멱등 no-op(200), active 누락·형식 오류는 400(INVALID_PARAMETER),
     * 없으면 404(WAREHOUSE_NOT_FOUND), version 불일치는 409(OPTIMISTIC_LOCK_CONFLICT).
     */
    @Operation(
            summary = "창고 활성 상태 전환",
            description = "창고를 활성↔비활성으로 전환한다. 같은 값으로의 전환은 멱등(no-op)이며 version으로 낙관적 락을 검증한다."
    )
    @PatchMapping("/{id}/active")
    public WarehouseActiveResponse changeActive(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "창고 내부 PK")
            @PathVariable Long id,
            @Valid @RequestBody WarehouseActiveRequest request
    ) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER);
        ChangeWarehouseActiveCommand command =
                new ChangeWarehouseActiveCommand(request.active(), request.version());
        WarehouseSummaryForEdit detail = warehouseService.changeActive(id, command);
        return WarehouseActiveResponse.from(detail);
    }
}
