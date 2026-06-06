package com.fallguys.inventoryservice.stock.infrastructure.persistence;

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
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSortField;
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
        return jpaDao.save(StockEntity.from(stock)).getId();
    }

    @Override
    public Optional<StockCreateResult> findResultById(Long id) {
        return jpaDao.findResultById(id);
    }

    @Override
    public Optional<StockDetail> findDetailByWarehouseCodeAndSku(String warehouseCode, String sku) {
        return jpaDao.findDetailByWarehouseCodeAndSku(warehouseCode, sku);
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
