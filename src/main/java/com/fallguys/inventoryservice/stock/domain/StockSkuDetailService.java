package com.fallguys.inventoryservice.stock.domain;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockSkuDetailService {

    private static final int RECENT_HISTORY_LIMIT = 5;

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;

    /**
     * sku 상세 패널을 조회한다. Tenancy에 따라 집계 범위가 다르다(BRANCH는 자기 창고 1곳).
     *
     * 흐름:
     * 1) BRANCH는 자기 창고(tenancy_code)로, ADMIN·HQ는 전사로 조회 범위를 정한다.
     * 2) sku의 창고별 재고 행을 조회한다. 범위 내에 한 건도 없으면 404(존재 은닉) — 소속 외 sku도 "없음"으로 응답.
     * 3) 전체 현재고·안전재고 합계를 계산하고, 같은 범위로 최근 이동 이력 5건을 덧붙인다.
     *
     * 트랜잭션: 읽기 전용. 외부 호출 없음.
     *
     * 예외:
     * - 범위 내 재고 없음(존재 은닉 포함): StockNotFoundException (404)
     */
    @Transactional(readOnly = true)
    public StockSkuDetail getSkuDetail(String sku, TenancyType tenancyType, String tenancyCode) {
        List<String> scope = tenancyType == TenancyType.BRANCH ? List.of(tenancyCode) : List.of();
        List<StockSkuRow> rows = stockRepository.findSkuWarehouseStocks(sku, scope);
        if (rows.isEmpty()) {
            throw new StockNotFoundException(sku);
        }
        int totalQuantity = rows.stream().mapToInt(StockSkuRow::quantity).sum();
        int totalSafetyStock = rows.stream().mapToInt(StockSkuRow::safetyStock).sum();
        List<MovementHistory> history = stockMovementRepository.findRecentBySku(sku, scope, RECENT_HISTORY_LIMIT);
        // TODO(Item 연동): 대분류·중분류를 Item 서비스에서 internal 조회해 StockSkuDetail에 담는다(현재는 응답에서 null).
        //  - stock/domain에 의도 기반 DIP 인터페이스(예: ItemCategoryProvider) + VO(ItemCategory)를 정의해 주입하고 sku로 조회.
        //  - 구현체는 stock/infrastructure/client(예: ItemFeignClient + ItemCategoryProviderAdapter, 에러 번역은 §10).
        //  - StockSkuDetail에 majorCategory/middleCategory 필드를 추가해 여기서 채운다.
        //  - 주의: 외부 호출은 이 readOnly DB 트랜잭션 밖에서 수행한다(네트워크 동안 DB 커넥션 점유 방지) → DB 조회와 분리할 것.
        return new StockSkuDetail(sku, rows.get(0).itemName(), rows.get(0).itemUnit(), totalQuantity, totalSafetyStock, rows, history);
    }
}
