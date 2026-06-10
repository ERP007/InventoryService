package com.fallguys.inventoryservice.stock.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fallguys.inventoryservice.shared.security.JwtClaimExtractor;
import com.fallguys.inventoryservice.stock.controller.dto.InboundRequest;
import com.fallguys.inventoryservice.stock.controller.dto.InboundResponse;
import com.fallguys.inventoryservice.stock.domain.StockInboundService;
import com.fallguys.inventoryservice.stock.domain.query.InboundResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 서비스 간 내부 호출용 재고 입고 API. 사설망 전용이며 게이트웨이가 외부 노출을 차단한다.
 * 인증(JWT)은 리소스 서버가 강제하고 수행자(사번·이름)는 전파된 JWT에서 추출한다. 사용자 Role 게이팅은 없다.
 */
@RestController
@RequestMapping("/internal/inventory/stocks")
@RequiredArgsConstructor
@Tag(name = "Stock Internal", description = "재고 내부 연계 API")
public class StockInboundController {

    private final StockInboundService stockInboundService;

    /**
     * PO 입고·SO 도착으로 한 창고의 여러 라인을 입고한다. 같은 sourceRef 재호출은 이전 결과를 그대로 반환한다(멱등).
     * 형식 오류(lines 빈 배열·수량 ≤0·필드 누락)는 400, 창고 없음은 404, 비활성 창고는 400,
     * 재고 행 없음(신규 생성 미지원)은 404로 매핑된다.
     */
    @Operation(
            summary = "재고 입고(내부)",
            description = "PO 입고·SO 도착의 여러 라인을 한 트랜잭션으로 증가시키고 INBOUND 이동 이력을 라인 수만큼 기록한다. "
                    + "수행자는 전파된 JWT에서 추출한다. 같은 sourceRef 재호출은 멱등(이전 결과 반환)."
    )
    @PostMapping("/inbound")
    public ResponseEntity<InboundResponse> inbound(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InboundRequest request) {
        String executorEmpNo = JwtClaimExtractor.extractEmployeeNo(jwt);
        String executorName = JwtClaimExtractor.extractName(jwt);
        InboundResult result = stockInboundService.inbound(request.toCommand(executorEmpNo, executorName));
        return ResponseEntity.ok(InboundResponse.from(result));
    }
}
