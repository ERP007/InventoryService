package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.query.DailyMovementCount;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockKpi;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

class StockKpiServiceTest {

    @Test
    void ADMIN은_전사범위로_집계하고_최근7일_기준으로_조정수를_센다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.counts = new StockStatusCount(20, 4); // 부족(재고0 포함) 4, 정상 16
        StubMovementRepository movementRepo = new StubMovementRepository();
        movementRepo.recentCount = 8;
        StockKpiService service = new StockKpiService(stockRepo, movementRepo);
        Instant now = Instant.parse("2026-06-07T08:00:00Z");

        StockKpi kpi = service.getKpi(TenancyType.ADMIN, null, now);

        assertThat(stockRepo.scope).isEmpty();
        assertThat(movementRepo.scope).isEmpty();
        assertThat(movementRepo.since).isEqualTo(now.minus(Duration.ofDays(7)));
        assertThat(kpi.totalSkuCount()).isEqualTo(20);
        assertThat(kpi.lowStockCount()).isEqualTo(4);
        assertThat(kpi.fulfillmentRate()).isEqualTo(80.0); // 정상 16 / 총 20
        assertThat(kpi.recentAdjustCount()).isEqualTo(8);
    }

    @Test
    void BRANCH는_자기창고로_범위를_강제한다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.counts = new StockStatusCount(5, 2);
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockKpiService service = new StockKpiService(stockRepo, movementRepo);

        service.getKpi(TenancyType.BRANCH, "WH-SE-001", Instant.parse("2026-06-07T08:00:00Z"));

        assertThat(stockRepo.scope).containsExactly("WH-SE-001");
        assertThat(movementRepo.scope).containsExactly("WH-SE-001");
    }

    @Test
    void 총_포지션이_0이면_충족률은_0이다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.counts = new StockStatusCount(0, 0);
        StockKpiService service = new StockKpiService(stockRepo, new StubMovementRepository());

        StockKpi kpi = service.getKpi(TenancyType.ADMIN, null, Instant.parse("2026-06-07T08:00:00Z"));

        assertThat(kpi.totalSkuCount()).isZero();
        assertThat(kpi.lowStockCount()).isZero();
        assertThat(kpi.fulfillmentRate()).isEqualTo(0.0);
    }

    @Test
    void 충족률은_소수1자리로_반올림한다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.counts = new StockStatusCount(3, 1); // 정상 2 / 총 3 = 66.666… → 66.7
        StockKpiService service = new StockKpiService(stockRepo, new StubMovementRepository());

        StockKpi kpi = service.getKpi(TenancyType.ADMIN, null, Instant.parse("2026-06-07T08:00:00Z"));

        assertThat(kpi.fulfillmentRate()).isEqualTo(66.7);
    }

    private static final class StubStockRepository implements StockRepository {
        private StockStatusCount counts = new StockStatusCount(0, 0);
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
        public List<StockQuantity> findQuantitiesByWarehouseCodeAndSkus(String warehouseCode, List<String> skus) {
            return List.of();
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

        @Override
        public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
            return Optional.empty();
        }

        @Override
        public Optional<Stock> findBySkuAndWarehouseIdForUpdate(String sku, Long warehouseId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit> findSafetyStockEdit(
                String warehouseCode, String sku) {
            return Optional.empty();
        }

        @Override
        public com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit updateSafetyStock(
                com.fallguys.inventoryservice.stock.domain.command.UpdateSafetyStockCommand command) {
            return null;
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
        public List<DailyMovementCount> countDailyByType(List<String> warehouseCodes, LocalDate from, LocalDate to) {
            return List.of();
        }

        @Override
        public MovementSummaryPage search(MovementSearchQuery query) {
            return null;
        }

        @Override
        public List<MovementHistory> findRecentBySku(String sku, List<String> warehouseCodes, int limit) {
            return List.of();
        }

        @Override
        public StockMovement save(StockMovement movement) {
            return movement;
        }

        @Override
        public List<com.fallguys.inventoryservice.stock.domain.query.InboundMovement> findInboundBySourceRefAndWarehouseCode(
                String sourceRef, String warehouseCode) {
            return List.of();
        }

        @Override
        public List<com.fallguys.inventoryservice.stock.domain.query.OutboundMovement> findOutboundBySourceRefAndWarehouseCode(
                String sourceRef, String warehouseCode) {
            return List.of();
        }
    }
}
