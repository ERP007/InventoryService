package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

class StockSkuDetailServiceTest {

    @Test
    void ADMIN은_전사범위로_조회하고_합계와_이력을_조립한다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.rows = List.of(
                new StockSkuRow("엔진오일 필터", 2L, "WH-SE-001", "서울 1창고", 48, 50),
                new StockSkuRow("엔진오일 필터", 1L, "HQ-001", "본사", 100, 100));
        StubMovementRepository movementRepo = new StubMovementRepository();
        movementRepo.history = List.of(
                new MovementHistory(MovementType.OUTBOUND, -18, "AD002", Instant.parse("2026-05-20T14:22:00Z")));
        StockSkuDetailService service = new StockSkuDetailService(stockRepo, movementRepo);

        StockSkuDetail detail = service.getSkuDetail("HMC-EN-00214", TenancyType.ADMIN, null);

        assertThat(stockRepo.scope).isEmpty();      // 전사
        assertThat(movementRepo.scope).isEmpty();
        assertThat(movementRepo.limit).isEqualTo(5);
        assertThat(detail.itemName()).isEqualTo("엔진오일 필터");
        assertThat(detail.totalQuantity()).isEqualTo(148);
        assertThat(detail.totalSafetyStock()).isEqualTo(150);
        assertThat(detail.warehouses()).hasSize(2);
        assertThat(detail.history()).hasSize(1);
    }

    @Test
    void BRANCH는_자기창고로_범위를_강제한다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.rows = List.of(new StockSkuRow("엔진오일 필터", 2L, "WH-SE-001", "서울 1창고", 48, 50));
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockSkuDetailService service = new StockSkuDetailService(stockRepo, movementRepo);

        service.getSkuDetail("HMC-EN-00214", TenancyType.BRANCH, "WH-SE-001");

        assertThat(stockRepo.scope).containsExactly("WH-SE-001");
        assertThat(movementRepo.scope).containsExactly("WH-SE-001");
    }

    @Test
    void 범위내_재고가_없으면_StockNotFoundException() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.rows = List.of();
        StockSkuDetailService service = new StockSkuDetailService(stockRepo, new StubMovementRepository());

        assertThatThrownBy(() -> service.getSkuDetail("NO-SUCH", TenancyType.ADMIN, null))
                .isInstanceOf(StockNotFoundException.class);
    }

    private static final class StubStockRepository implements StockRepository {
        private List<StockSkuRow> rows = List.of();
        private List<String> scope;

        @Override
        public List<StockSkuRow> findSkuWarehouseStocks(String sku, List<String> warehouseCodes) {
            this.scope = warehouseCodes;
            return rows;
        }

        @Override
        public StockSummaryPage search(StockSearchQuery query) {
            return null;
        }

        @Override
        public Optional<StockDetail> findDetailByWarehouseCodeAndSku(String warehouseCode, String sku) {
            return Optional.empty();
        }

        @Override
        public boolean existsBySkuAndWarehouseId(String sku, Long warehouseId) {
            return false;
        }

        @Override
        public Long save(Stock stock) {
            return null;
        }

        @Override
        public Optional<StockCreateResult> findResultById(Long id) {
            return Optional.empty();
        }

        @Override
        public StockStatusCount countByStatus(List<String> warehouseCodes) {
            return new StockStatusCount(0, 0, 0);
        }

        @Override
        public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
            return Optional.empty();
        }
    }

    private static final class StubMovementRepository implements StockMovementRepository {
        private List<MovementHistory> history = List.of();
        private List<String> scope;
        private int limit;

        @Override
        public List<MovementHistory> findRecentBySku(String sku, List<String> warehouseCodes, int limit) {
            this.scope = warehouseCodes;
            this.limit = limit;
            return history;
        }

        @Override
        public MovementSummaryPage search(MovementSearchQuery query) {
            return null;
        }

        @Override
        public long countRecent(List<String> warehouseCodes, Instant since) {
            return 0;
        }

        @Override
        public StockMovement save(StockMovement movement) {
            return movement;
        }
    }
}
