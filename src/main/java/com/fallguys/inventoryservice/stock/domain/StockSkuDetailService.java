package com.fallguys.inventoryservice.stock.domain;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.exception.ItemInactiveException;
import com.fallguys.inventoryservice.stock.domain.exception.ItemServiceUnavailableException;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.ItemInfo;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockSkuDetailService {

    private static final int RECENT_HISTORY_LIMIT = 5;
    private static final Logger log = LoggerFactory.getLogger(StockSkuDetailService.class);

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ItemInfoProvider itemInfoProvider;

    /**
     * sku 상세 패널을 조회한다. Tenancy에 따라 집계 범위가 다르다(BRANCH는 자기 창고 1곳).
     *
     * 흐름:
     * 1) BRANCH는 자기 창고(tenancy_code)로, ADMIN·HQ는 전사로 조회 범위를 정한다.
     * 2) sku의 창고별 재고 행을 조회한다. 범위 내에 한 건도 없으면 404(존재 은닉). 부품이 비활성이면 400(상세는 활성 부품만 제공).
     * 3) 전체 현재고·안전재고 합계와 최근 이동 이력 5건을 모은다(부품명·단위는 stock 스냅샷).
     * 4) 대분류·중분류는 외부 Item 서비스에서 조회한다. 통합 비활성/호출 실패 시 null로 강등한다(패널은 정상 반환).
     *
     * 트랜잭션: DB 조회는 각 리포지토리(Spring Data) 읽기 트랜잭션으로 수행되며 투영(record)이라 지연로딩이 없다.
     *  외부 Item 호출이 DB 트랜잭션에 감싸여 커넥션을 점유하지 않도록, 메서드 전체에 @Transactional을 두지 않는다.
     *
     * 예외:
     * - 범위 내 재고 없음(존재 은닉 포함): StockNotFoundException (404)
     * - 비활성 부품(SKU): ItemInactiveException (400) — 상세 패널은 활성 부품만 제공한다
     * - Item 호출 실패: 내부에서 잡아 카테고리를 null로 강등(예외를 전파하지 않음)
     */
    public StockSkuDetail getSkuDetail(String sku, TenancyType tenancyType, String tenancyCode) {
        List<String> scope = tenancyType == TenancyType.BRANCH ? List.of(tenancyCode) : List.of();
        List<StockSkuRow> rows = stockRepository.findSkuWarehouseStocks(sku, scope);
        if (rows.isEmpty()) {
            throw new StockNotFoundException(sku);
        }
        // 비활성 부품(SKU)은 상세 패널을 제공하지 않는다(목록·이력엔 노출되지만 상세 조회는 막는다).
        if (rows.stream().anyMatch(row -> !row.itemActive())) {
            throw new ItemInactiveException(sku);
        }
        int totalQuantity = rows.stream().mapToInt(StockSkuRow::quantity).sum();
        int totalSafetyStock = rows.stream().mapToInt(StockSkuRow::safetyStock).sum();
        List<MovementHistory> history = stockMovementRepository.findRecentBySku(sku, scope, RECENT_HISTORY_LIMIT);
        ItemInfo itemInfo = fetchItemInfoOrNull(sku);
        return new StockSkuDetail(
                sku, rows.get(0).itemName(), rows.get(0).itemUnit(),
                itemInfo == null ? null : itemInfo.majorCategory(), // GET internal/items/{sku} 구현 완료 & 정상 호출 시 값 매핑
                itemInfo == null ? null : itemInfo.middleCategory(),
                totalQuantity, totalSafetyStock, rows, history);
    }

    /**
     * 대분류·중분류를 Item 서비스에서 조회한다(트랜잭션 밖). 통합 비활성·부품 없음이면 null,
     * 기술적 호출 실패면 WARN 로그 후 null로 강등한다 — 카테고리는 패널의 부가 정보라 실패해도 패널은 정상 반환한다.
     */
    private ItemInfo fetchItemInfoOrNull(String sku) {
        try {
            return itemInfoProvider.findBySku(sku).orElse(null);
        } catch (ItemServiceUnavailableException e) {
            log.warn("Item 정보 조회 실패 — 대분류·중분류를 null로 강등합니다. sku={}", sku);
            return null;
        }
    }
}
