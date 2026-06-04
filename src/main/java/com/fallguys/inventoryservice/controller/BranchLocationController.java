package com.fallguys.inventoryservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.controller.dto.BranchLocationCreateRequest;
import com.fallguys.inventoryservice.controller.dto.BranchLocationListResponse;
import com.fallguys.inventoryservice.controller.dto.BranchLocationResponse;
import com.fallguys.inventoryservice.domain.BranchLocation;
import com.fallguys.inventoryservice.domain.BranchLocationService;
import com.fallguys.inventoryservice.domain.command.CreateBranchLocationCommand;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/inventory/branch-locations")
@RequiredArgsConstructor
@Tag(name = "BranchLocation", description = "소속 지점 마스터 API")
public class BranchLocationController {

    private final BranchLocationService branchLocationService;

    /**
     * 신규 지점을 등록한다. ADMIN·HQ_MANAGER만 호출 가능(인가는 게이트웨이가 판단).
     * 형식 검증 실패는 400, 지점명 중복은 409로 매핑된다(GlobalExceptionHandler).
     */
    @Operation(
            summary = "지점 등록",
            description = "신규 소속 지점을 마스터에 등록한다. 지점명은 trim 후 1~100자이며 시스템 내 유일하다."
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchLocationResponse create(@Valid @RequestBody BranchLocationCreateRequest request) {
        BranchLocation created = branchLocationService.create(new CreateBranchLocationCommand(request.name()));
        return BranchLocationResponse.from(created);
    }

    /**
     * 전체 지점 목록을 조회한다. ADMIN·HQ_MANAGER만 호출 가능(인가는 게이트웨이가 판단).
     * 창고 추가/수정 모달의 소속 지점 드롭다운 채움용. 0건이어도 200과 빈 배열을 반환한다.
     */
    @Operation(
            summary = "지점 목록 조회",
            description = "전체 소속 지점을 id 오름차순으로 반환한다. 검색·필터 파라미터는 없으며, 0건이면 빈 배열을 반환한다."
    )
    @GetMapping
    public BranchLocationListResponse list() {
        return BranchLocationListResponse.from(branchLocationService.findAll());
    }
}
