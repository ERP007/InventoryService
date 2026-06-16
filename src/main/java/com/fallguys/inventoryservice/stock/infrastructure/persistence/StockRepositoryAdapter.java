package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.fallguys.inventoryservice.shared.exception.OptimisticLockConflictException;
import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockRepository;
import com.fallguys.inventoryservice.stock.domain.command.UpdateSafetyStockCommand;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.ItemStockRow;
import com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit;
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
    public List<ItemStockRow> findRecentItemStocks(String sku, List<String> warehouseCodes, int limit) {
        return jpaDao.findRecentItemStocks(sku, !warehouseCodes.isEmpty(), warehouseCodes, PageRequest.of(0, limit));
    }

    @Override
    public List<String> findWarehouseCodesBySku(String sku) {
        return jpaDao.findWarehouseCodesBySku(sku);
    }

    @Override
    public int updateItemNameBySku(String sku, String itemName) {
        return jpaDao.updateItemNameBySku(sku, itemName);
    }

    @Override
    public StockStatusCount countByStatus(List<String> warehouseCodes) {
        return jpaDao.countByStatus(!warehouseCodes.isEmpty(), warehouseCodes);
    }

    @Override
    public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
        return jpaDao.findBySkuAndWarehouseCode(sku, warehouseCode).map(StockEntity::toDomain);
    }

    @Override
    public Optional<Stock> findBySkuAndWarehouseIdForUpdate(String sku, Long warehouseId) {
        return jpaDao.findBySkuAndWarehouseIdForUpdate(sku, warehouseId).map(StockEntity::toDomain);
    }

    @Override
    public Optional<SafetyStockEdit> findSafetyStockEdit(String warehouseCode, String sku) {
        return jpaDao.findBySkuAndWarehouseCode(sku, warehouseCode)
                .map(entity -> toSafetyStockEdit(warehouseCode, entity));
    }

    /**
     * 안전재고를 교체한다(load-modify 방식). 클라이언트 version과 현재 version을 명시 비교해 다르면 409로 막고(분실 갱신 방지의 핵심),
     * flush 시 @Version이 한 번 더 동시 수정을 검증한다(그 실패도 409로 번역). 갱신 결과를 읽기 모델로 반환한다.
     */
    @Override
    public SafetyStockEdit updateSafetyStock(UpdateSafetyStockCommand command) {
        StockEntity entity = jpaDao.findBySkuAndWarehouseCode(command.sku(), command.warehouseCode())
                .orElseThrow(() -> new StockNotFoundException(command.warehouseCode(), command.sku()));
        if (!Objects.equals(entity.getVersion(), command.version())) {
            throw new OptimisticLockConflictException(
                    "재고가 이미 변경되었습니다. 최신 상태로 재조회 후 다시 시도하세요.");
        }
        entity.updateSafetyStock(command.safetyStock());
        try {
            jpaDao.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new OptimisticLockConflictException(
                    "재고가 이미 변경되었습니다. 최신 상태로 재조회 후 다시 시도하세요.");
        }
        return toSafetyStockEdit(command.warehouseCode(), entity);
    }

    /** 영속 엔티티를 안전재고 조정 읽기 모델로 변환한다(warehouseCode는 조회 입력을 그대로 사용 — 엔티티엔 코드가 없다). */
    private SafetyStockEdit toSafetyStockEdit(String warehouseCode, StockEntity entity) {
        return new SafetyStockEdit(
                entity.getSku(), warehouseCode, entity.getItemName(), entity.getItemUnit(),
                entity.getCurrentStock(), entity.getSafetyStock(), entity.getVersion());
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
