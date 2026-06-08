package com.fallguys.inventoryservice.stock.domain;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository stockMovementRepository;

    /**
     * 재고 이동 이력을 조회한다. Tenancy에 따라 조회 범위가 다르다.
     *
     * 흐름:
     * 1) BRANCH 사용자는 자기 창고(tenancy_code)로만 한정한다 — 요청의 창고 필터를 자기 창고로 강제 교체.
     * 2) ADMIN·HQ는 전사 범위로 요청 조건(창고 다중 필터 포함)을 그대로 사용한다.
     * 3) 검증된 조건으로 영속성에서 페이지 조회한다(기간은 어댑터가 KST 일자→Instant 구간으로 변환).
     *
     * 트랜잭션: 읽기 전용. 외부 호출 없음. 매칭 0건이면 빈 페이지.
     */
    @Transactional(readOnly = true)
    public MovementSummaryPage search(MovementSearchQuery query, TenancyType tenancyType, String tenancyCode) {
        MovementSearchQuery effective = tenancyType == TenancyType.BRANCH
                ? query.withWarehouseCodes(List.of(tenancyCode))
                : query;
        return stockMovementRepository.search(effective);
    }
}
