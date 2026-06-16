package com.fallguys.inventoryservice.stock.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.shared.model.UserRole;
import com.fallguys.inventoryservice.shared.security.JwtClaimExtractor;
import com.fallguys.inventoryservice.stock.controller.dto.ItemActiveSyncRequest;
import com.fallguys.inventoryservice.stock.controller.dto.ItemNameSyncRequest;
import com.fallguys.inventoryservice.stock.controller.dto.ItemSyncResponse;
import com.fallguys.inventoryservice.stock.controller.dto.ItemUnitSyncRequest;
import com.fallguys.inventoryservice.stock.domain.StockItemSyncService;
import com.fallguys.inventoryservice.stock.domain.query.ItemSyncResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 서비스 간 내부 호출용 아이템 마스터 동기화 API. 사설망 전용이며 게이트웨이가 외부 노출을 차단한다.
 * 인증(JWT)을 검증하고 ADMIN·HQ_MANAGER·HQ_STAFF Role만 허용한다(그 외 403).
 */
@RestController
@RequestMapping("/internal/inventory/items")
@RequiredArgsConstructor
@Tag(name = "Item Internal", description = "아이템 마스터 동기화 내부 연계 API")
public class ItemInternalController {

    private final StockItemSyncService stockItemSyncService;

    /**
     * Item 마스터의 부품명 변경을 해당 sku의 모든 stock 행에 동기화한다. ADMIN·HQ_MANAGER·HQ_STAFF 전용(그 외 403).
     * 대상 행이 없어도(미적재 부품) 200으로 정상 반환한다. itemName 누락·공백은 400(INVALID_PARAMETER)으로 매핑된다.
     */
    @Operation(
            summary = "아이템 이름 동기화(내부)",
            description = "Item 마스터 부품명 수정 시 호출. 해당 sku의 모든 창고 stock 행 item_name을 일괄 갱신하고 "
                    + "변경 행 수·창고 코드 목록을 반환한다. 멱등(절대값 교체)."
    )
    @PatchMapping("/{sku}/name")
    public ResponseEntity<ItemSyncResponse> syncItemName(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "부품 SKU") @PathVariable String sku,
            @Valid @RequestBody ItemNameSyncRequest request) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER, UserRole.HQ_STAFF);
        ItemSyncResult result = stockItemSyncService.syncItemName(sku, request.itemName());
        return ResponseEntity.ok(ItemSyncResponse.from(result));
    }

    /**
     * Item 마스터의 단위 변경을 해당 sku의 모든 stock 행에 동기화한다. ADMIN·HQ_MANAGER·HQ_STAFF 전용(그 외 403).
     * 대상 행이 없어도(미적재 부품) 200으로 정상 반환한다. itemUnit 누락·허용 밖 값은 400(INVALID_PARAMETER)으로 매핑된다.
     */
    @Operation(
            summary = "아이템 단위 동기화(내부)",
            description = "Item 마스터 단위 수정 시 호출. 해당 sku의 모든 창고 stock 행 item_unit을 일괄 갱신하고 "
                    + "변경 행 수·창고 코드 목록을 반환한다. itemUnit은 EA/BOX/SET/L. 멱등(절대값 교체)."
    )
    @PatchMapping("/{sku}/unit")
    public ResponseEntity<ItemSyncResponse> syncItemUnit(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "부품 SKU") @PathVariable String sku,
            @Valid @RequestBody ItemUnitSyncRequest request) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER, UserRole.HQ_STAFF);
        ItemSyncResult result = stockItemSyncService.syncItemUnit(sku, request.itemUnit());
        return ResponseEntity.ok(ItemSyncResponse.from(result));
    }

    /**
     * Item 마스터의 활성 여부 변경을 해당 sku의 모든 stock 행에 동기화한다. ADMIN·HQ_MANAGER·HQ_STAFF 전용(그 외 403).
     * 대상 행이 없어도(미적재 부품) 200으로 정상 반환한다. active 누락·형식 오류는 400(INVALID_PARAMETER)으로 매핑된다.
     */
    @Operation(
            summary = "아이템 활성 여부 동기화(내부)",
            description = "Item 마스터 활성/비활성 토글 시 호출. 해당 sku의 모든 창고 stock 행 item_active를 일괄 갱신하고 "
                    + "변경 행 수·창고 코드 목록을 반환한다. 비활성 전환 시 이후 입출고·조정이 차단된다. 멱등(절대값 교체)."
    )
    @PatchMapping("/{sku}/active")
    public ResponseEntity<ItemSyncResponse> syncItemActive(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "부품 SKU") @PathVariable String sku,
            @Valid @RequestBody ItemActiveSyncRequest request) {
        JwtClaimExtractor.requireAnyOf(jwt, UserRole.ADMIN, UserRole.HQ_MANAGER, UserRole.HQ_STAFF);
        ItemSyncResult result = stockItemSyncService.syncItemActive(sku, request.active());
        return ResponseEntity.ok(ItemSyncResponse.from(result));
    }
}
