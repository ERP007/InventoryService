package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Repository;

import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockRepository;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockSortField;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummary;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class StockRepositoryAdapter implements StockRepository {

    private final StockJpaDao jpaDao;

    @Override
    public StockSummaryPage search(StockSearchQuery query) {
        Pageable pageable = PageRequest.of(query.page() - 1, query.size(),
                toSort(query.sortField(), query.sortDirection()));
        Page<StockSummary> page = jpaDao.search(
                toLikePattern(query.keyword()),
                query.hasWarehouseFilter(),
                query.warehouseCodes(),
                query.status() == null ? null : query.status().name(),
                pageable);
        return new StockSummaryPage(
                page.getContent(), query.page(), query.size(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public boolean existsBySkuAndWarehouseId(String sku, Long warehouseId) {
        return jpaDao.existsBySkuAndWarehouseId(sku, warehouseId);
    }

    @Override
    public Long save(Stock stock) {
        if (stock.getId() == null) {
            return jpaDao.save(StockEntity.from(stock)).getId();
        }
        // 기존 행: 같은 트랜잭션의 영속 엔티티를 조회(1차 캐시 적중)해 변동을 반영한다 → flush 시 @Version 검증.
        // 도메인의 "재고 없음"(404)은 서비스가 findBySkuAndWarehouseCode 단계에서 이미 처리한다.
        // 여기까지 와서 못 찾는 건 같은 트랜잭션 1차 캐시상 도달 불가한 내부 모순(또는 미존재 id로의 오용)이므로,
        // 도메인 예외(StockNotFoundException, 404)가 아니라 IllegalStateException(→500)으로 둔다.
        StockEntity entity = jpaDao.findById(stock.getId())
                .orElseThrow(() -> new IllegalStateException("수정할 재고를 찾지 못했습니다: " + stock.getId()));
        entity.update(stock);
        return jpaDao.save(entity).getId();
    }

    @Override
    public Optional<StockCreateResult> findResultById(Long id) {
        return jpaDao.findResultById(id);
    }

    @Override
    public Optional<StockDetail> findDetailByWarehouseCodeAndSku(String warehouseCode, String sku) {
        return jpaDao.findDetailByWarehouseCodeAndSku(warehouseCode, sku);
    }

    @Override
    public List<StockQuantity> findQuantitiesByWarehouseCodeAndSkus(String warehouseCode, List<String> skus) {
        return jpaDao.findQuantitiesByWarehouseCodeAndSkus(warehouseCode, skus);
    }

    @Override
    public List<StockSkuRow> findSkuWarehouseStocks(String sku, List<String> warehouseCodes) {
        return jpaDao.findSkuWarehouseStocks(sku, !warehouseCodes.isEmpty(), warehouseCodes);
    }

    @Override
    public StockStatusCount countByStatus(List<String> warehouseCodes) {
        return jpaDao.countByStatus(!warehouseCodes.isEmpty(), warehouseCodes);
    }

    @Override
    public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
        return jpaDao.findBySkuAndWarehouseCode(sku, warehouseCode).map(StockEntity::toDomain);
    }

    /**
     * 도메인 정렬 조건을 JPQL ORDER BY 식으로 변환한다(기술 의존은 이 계층에 한정).
     * safetyRatio는 계산식이며 NULLIF로 안전재고 0의 0-나눗셈을 회피한다. 안정 정렬을 위해 id 오름차순을 tie-breaker로 덧붙인다.
     */
    private Sort toSort(StockSortField field, SortDirection direction) {
        Sort.Direction springDirection = direction == SortDirection.DESC ? Sort.Direction.DESC : Sort.Direction.ASC;
        String expression = switch (field) {
            case NAME -> "s.itemName";
            case QUANTITY -> "s.currentStock";
            case SAFETY_RATIO -> "(s.currentStock * 1.0 / NULLIF(s.safetyStock, 0))";
            case LAST_ADJUSTED_AT -> "s.updatedAt";
        };
        return JpaSort.unsafe(springDirection, expression)
                .and(JpaSort.unsafe(Sort.Direction.ASC, "s.id"));
    }

    /** 부분 일치 LIKE 패턴으로 변환한다(소문자화 + 양끝 와일드카드). 검색어가 없으면 null. */
    private String toLikePattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        return "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }
}
