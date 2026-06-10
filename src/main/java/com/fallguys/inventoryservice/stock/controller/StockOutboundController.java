package com.fallguys.inventoryservice.stock.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.shared.security.JwtClaimExtractor;
import com.fallguys.inventoryservice.stock.controller.dto.OutboundRequest;
import com.fallguys.inventoryservice.stock.controller.dto.OutboundResponse;
import com.fallguys.inventoryservice.stock.domain.StockOutboundService;
import com.fallguys.inventoryservice.stock.domain.query.OutboundResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 서비스 간 내부 호출용 재고 출고 API. 사설망 전용이며 게이트웨이가 외부 노출을 차단한다.
 * 인증(JWT)은 리소스 서버가 강제하고 수행자(사번·이름)는 전파된 JWT에서 추출한다. 사용자 Role 게이팅은 없다.
 */
@RestController
@RequestMapping("/internal/inventory/stocks")
@RequiredArgsConstructor
@Tag(name = "Stock Internal", description = "재고 내부 연계 API")
public class StockOutboundController {

    private final StockOutboundService stockOutboundService;

    /**
     * SO 출고로 한 창고의 여러 라인을 차감한다. 같은 sourceRef 재호출은 이전 결과를 그대로 반환한다(멱등).
     * 형식 오류(lines 빈 배열·수량 ≤0·필드 누락)는 400, 비활성 창고는 400, 창고·재고 없음은 404,
     * 가용재고 부족은 409(INSUFFICIENT_STOCK), 잠금 대기 초과는 409(LOCK_TIMEOUT)로 매핑된다.
     */
    @Operation(
            summary = "재고 출고(내부)",
            description = "SO 출고의 여러 라인을 한 트랜잭션으로 차감하고 OUTBOUND 이동 이력을 라인 수만큼 기록한다. "
                    + "가용재고를 검증하고 (sku×창고) 비관락으로 동시 출고를 직렬화한다. "
                    + "수행자는 전파된 JWT에서 추출한다. 같은 sourceRef 재호출은 멱등(이전 결과 반환)."
    )
    @PostMapping("/outbound")
    public ResponseEntity<OutboundResponse> outbound(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OutboundRequest request) {
        String executorEmpNo = JwtClaimExtractor.extractEmployeeNo(jwt);
        String executorName = JwtClaimExtractor.extractName(jwt);
        OutboundResult result = stockOutboundService.outbound(request.toCommand(executorEmpNo, executorName));
        return ResponseEntity.ok(OutboundResponse.from(result));
    }
}
