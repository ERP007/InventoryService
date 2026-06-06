package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.command.CreateStockCommand;
import com.fallguys.inventoryservice.stock.domain.exception.StockAlreadyExistsException;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;
import com.fallguys.inventoryservice.warehouse.domain.Warehouse;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseRepository;
import com.fallguys.inventoryservice.warehouse.domain.command.ChangeWarehouseActiveCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

class StockServiceTest {

    @Test
    void 정상생성은_코드를_id로_해석해_저장하고_조인된_결과를_반환한다() {
        StubStockRepository stockRepository = new StubStockRepository();
        StubWarehouseRepository warehouseRepository = new StubWarehouseRepository(2L);
        StockService service = new StockService(stockRepository, warehouseRepository);

        StockCreateResult result = service.create(
                new CreateStockCommand("HMC-EN-00214", "엔진오일 필터", "WH-SE-001", 100, 50));

        assertThat(result.stockId()).isEqualTo(1050L);
        assertThat(result.sku()).isEqualTo("HMC-EN-00214");
        assertThat(result.warehouseCode()).isEqualTo("WH-SE-001");
        assertThat(result.quantity()).isEqualTo(100);
        assertThat(result.safetyStock()).isEqualTo(50);
        assertThat(stockRepository.savedWarehouseId).isEqualTo(2L);
    }

    @Test
    void 창고가_없으면_WarehouseNotFoundException을_던지고_저장하지_않는다() {
        StubStockRepository stockRepository = new StubStockRepository();
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(null));

        assertThatThrownBy(() -> service.create(
                new CreateStockCommand("SKU", "부품", "NOPE", 10, 10)))
                .isInstanceOf(WarehouseNotFoundException.class);
        assertThat(stockRepository.saved).isNull();
    }

    @Test
    void 동일_재고가_이미_있으면_StockAlreadyExistsException을_던지고_저장하지_않는다() {
        StubStockRepository stockRepository = new StubStockRepository();
        stockRepository.exists = true;
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L));

        assertThatThrownBy(() -> service.create(
                new CreateStockCommand("HMC-EN-00214", "부품", "WH-SE-001", 10, 10)))
                .isInstanceOf(StockAlreadyExistsException.class);
        assertThat(stockRepository.saved).isNull();
    }

    @Test
    void search_BRANCH는_요청_창고필터를_무시하고_자기_창고로_강제한다() {
        StubStockRepository stockRepository = new StubStockRepository();
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L));
        StockSearchQuery query = StockSearchQuery.of(null, "WH-OTHER,HQ-001", null, null, null, null);

        service.search(query, TenancyType.BRANCH, "WH-SE-001");

        assertThat(stockRepository.searchArg.warehouseCodes()).containsExactly("WH-SE-001");
    }

    @Test
    void search_ADMIN은_요청_창고필터를_그대로_사용한다() {
        StubStockRepository stockRepository = new StubStockRepository();
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L));
        StockSearchQuery query = StockSearchQuery.of(null, "WH-SE-001,HQ-001", null, null, null, null);

        service.search(query, TenancyType.ADMIN, null);

        assertThat(stockRepository.searchArg.warehouseCodes()).containsExactly("WH-SE-001", "HQ-001");
    }

    private static final class StubStockRepository implements StockRepository {
        private boolean exists = false;
        private Stock saved;
        private Long savedWarehouseId;
        private StockSearchQuery searchArg;

        @Override
        public StockSummaryPage search(StockSearchQuery query) {
            this.searchArg = query;
            return new StockSummaryPage(List.of(), query.page(), query.size(), 0, 0);
        }

        @Override
        public boolean existsBySkuAndWarehouseId(String sku, Long warehouseId) {
            return exists;
        }

        @Override
        public Long save(Stock stock) {
            this.saved = stock;
            this.savedWarehouseId = stock.getWarehouseId();
            return 1050L;
        }

        @Override
        public Optional<StockCreateResult> findResultById(Long id) {
            return Optional.of(new StockCreateResult(
                    1050L, saved.getSku(), "WH-SE-001", saved.getQuantity(), saved.getSafetyStock(),
                    Instant.parse("2026-05-28T14:36:00Z")));
        }
    }

    private static final class StubWarehouseRepository implements WarehouseRepository {
        private final Long warehouseId; // null이면 미존재

        private StubWarehouseRepository(Long warehouseId) {
            this.warehouseId = warehouseId;
        }

        @Override
        public Optional<WarehouseSummaryForEdit> findForEditByCode(String code) {
            if (warehouseId == null) {
                return Optional.empty();
            }
            return Optional.of(new WarehouseSummaryForEdit(
                    warehouseId, code, "창고", WarehouseType.DEALER, 3L, "지점", "주소", true,
                    Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"), 0L));
        }

        @Override
        public List<WarehouseSummary> search(WarehouseSearchQuery query) {
            return List.of();
        }

        @Override
        public List<WarehouseHqSummary> findActiveHq() {
            return List.of();
        }

        @Override
        public boolean existsByCode(String code) {
            return false;
        }

        @Override
        public Long save(Warehouse warehouse) {
            return null;
        }

        @Override
        public Optional<WarehouseSummary> findSummaryById(Long id) {
            return Optional.empty();
        }

        @Override
        public WarehouseSummaryForEdit update(String code, UpdateWarehouseCommand command) {
            return null;
        }

        @Override
        public WarehouseSummaryForEdit changeActive(String code, ChangeWarehouseActiveCommand command) {
            return null;
        }
    }
}
