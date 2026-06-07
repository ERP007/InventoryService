package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockKpi;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

class StockKpiServiceTest {

    @Test
    void ADMIN은_전사범위로_집계하고_최근7일_기준으로_조정수를_센다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.counts = new StockStatusCount(20, 3, 1);
        StubMovementRepository movementRepo = new StubMovementRepository();
        movementRepo.recentCount = 8;
        StockKpiService service = new StockKpiService(stockRepo, movementRepo);
        Instant now = Instant.parse("2026-06-07T08:00:00Z");

        StockKpi kpi = service.getKpi(TenancyType.ADMIN, null, now);

        assertThat(stockRepo.scope).isEmpty();
        assertThat(movementRepo.scope).isEmpty();
        assertThat(movementRepo.since).isEqualTo(now.minus(Duration.ofDays(7)));
        assertThat(kpi.totalSkuCount()).isEqualTo(20);
        assertThat(kpi.lowStockCount()).isEqualTo(3);
        assertThat(kpi.noStockCount()).isEqualTo(1);
        assertThat(kpi.recentAdjustCount()).isEqualTo(8);
    }

    @Test
    void BRANCH는_자기창고로_범위를_강제한다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.counts = new StockStatusCount(5, 2, 0);
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockKpiService service = new StockKpiService(stockRepo, movementRepo);

        service.getKpi(TenancyType.BRANCH, "WH-SE-001", Instant.parse("2026-06-07T08:00:00Z"));

        assertThat(stockRepo.scope).containsExactly("WH-SE-001");
        assertThat(movementRepo.scope).containsExactly("WH-SE-001");
    }

    private static final class StubStockRepository implements StockRepository {
        private StockStatusCount counts = new StockStatusCount(0, 0, 0);
        private List<String> scope;

        @Override
        public StockStatusCount countByStatus(List<String> warehouseCodes) {
            this.scope = warehouseCodes;
            return counts;
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
        public List<StockSkuRow> findSkuWarehouseStocks(String sku, List<String> warehouseCodes) {
            return List.of();
        }
    }

    private static final class StubMovementRepository implements StockMovementRepository {
        private long recentCount;
        private List<String> scope;
        private Instant since;

        @Override
        public long countRecent(List<String> warehouseCodes, Instant since) {
            this.scope = warehouseCodes;
            this.since = since;
            return recentCount;
        }

        @Override
        public MovementSummaryPage search(MovementSearchQuery query) {
            return null;
        }

        @Override
        public List<MovementHistory> findRecentBySku(String sku, List<String> warehouseCodes, int limit) {
            return List.of();
        }
    }
}
