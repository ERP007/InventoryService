package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.command.CreateStockCommand;
import com.fallguys.inventoryservice.stock.domain.exception.ItemServiceUnavailableException;
import com.fallguys.inventoryservice.stock.domain.exception.StockAlreadyExistsException;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.ItemInfo;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.WarehouseStockQuery;
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

    // getDetail 외 테스트는 Item을 호출하지 않으므로 통합 활성·빈 결과의 no-op provider를 쓴다.
    private static final ItemInfoProvider ITEM_NOOP = sku -> Optional.empty();

    /** 통합 비활성(Item 미배포) provider — isEnabled=false. */
    private static ItemInfoProvider itemDisabled() {
        return new ItemInfoProvider() {
            @Override
            public Optional<ItemInfo> findBySku(String sku) {
                return Optional.empty();
            }

            @Override
            public boolean isEnabled() {
                return false;
            }
        };
    }

    @Test
    void 정상생성은_코드를_id로_해석해_저장하고_조인된_결과를_반환한다() {
        StubStockRepository stockRepository = new StubStockRepository();
        StubWarehouseRepository warehouseRepository = new StubWarehouseRepository(2L);
        StockService service = new StockService(stockRepository, warehouseRepository, ITEM_NOOP);

        StockCreateResult result = service.create(
                new CreateStockCommand("HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, "WH-SE-001", 100, 50));

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
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(null), ITEM_NOOP);

        assertThatThrownBy(() -> service.create(
                new CreateStockCommand("SKU", "부품", ItemUnit.EA, "NOPE", 10, 10)))
                .isInstanceOf(WarehouseNotFoundException.class);
        assertThat(stockRepository.saved).isNull();
    }

    @Test
    void 동일_재고가_이미_있으면_StockAlreadyExistsException을_던지고_저장하지_않는다() {
        StubStockRepository stockRepository = new StubStockRepository();
        stockRepository.exists = true;
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L), ITEM_NOOP);

        assertThatThrownBy(() -> service.create(
                new CreateStockCommand("HMC-EN-00214", "부품", ItemUnit.EA, "WH-SE-001", 10, 10)))
                .isInstanceOf(StockAlreadyExistsException.class);
        assertThat(stockRepository.saved).isNull();
    }

    @Test
    void search_BRANCH는_요청_창고필터를_무시하고_자기_창고로_강제한다() {
        StubStockRepository stockRepository = new StubStockRepository();
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L), ITEM_NOOP);
        StockSearchQuery query = StockSearchQuery.of(null, "WH-OTHER,HQ-001", null, null, null, null);

        service.search(query, TenancyType.BRANCH, "WH-SE-001");

        assertThat(stockRepository.searchArg.warehouseCodes()).containsExactly("WH-SE-001");
    }

    @Test
    void search_ADMIN은_요청_창고필터를_그대로_사용한다() {
        StubStockRepository stockRepository = new StubStockRepository();
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L), ITEM_NOOP);
        StockSearchQuery query = StockSearchQuery.of(null, "WH-SE-001,HQ-001", null, null, null, null);

        service.search(query, TenancyType.ADMIN, null);

        assertThat(stockRepository.searchArg.warehouseCodes()).containsExactly("WH-SE-001", "HQ-001");
    }

    @Test
    void getDetail_담당창고가_아니면_StockNotFoundException을_던진다() {
        StubStockRepository stockRepository = new StubStockRepository();
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L), ITEM_NOOP);

        // tenancy_code(WH-SE-001) != 요청 warehouseCode(WH-OTHER) → 404 은닉
        assertThatThrownBy(() -> service.getDetail("WH-OTHER", "EO-5W30-1L", "WH-SE-001"))
                .isInstanceOf(StockNotFoundException.class);
        assertThat(stockRepository.detailQueried).isFalse();
    }

    @Test
    void getDetail_재고행이_있으면_그_값을_반환한다() {
        StubStockRepository stockRepository = new StubStockRepository();
        stockRepository.detailResult = new StockDetail("WH-SE-001", "EO-5W30-1L", 48, 50);
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L), ITEM_NOOP);

        StockDetail detail = service.getDetail("WH-SE-001", "EO-5W30-1L", "WH-SE-001");

        assertThat(detail.quantity()).isEqualTo(48);
        assertThat(detail.safetyStock()).isEqualTo(50);
    }

    @Test
    void getDetail_재고행없고_통합비활성이면_quantity0_safetyStock0으로_graceful응답한다() {
        StubStockRepository stockRepository = new StubStockRepository();
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L), itemDisabled());

        StockDetail detail = service.getDetail("WH-SE-001", "UNKNOWN-SKU", "WH-SE-001");

        assertThat(detail.warehouseCode()).isEqualTo("WH-SE-001");
        assertThat(detail.sku()).isEqualTo("UNKNOWN-SKU");
        assertThat(detail.quantity()).isZero();
        assertThat(detail.safetyStock()).isZero();
    }

    @Test
    void getDetail_재고행없고_Item마스터에_있으면_quantity0_마스터안전재고로_응답한다() {
        StubStockRepository stockRepository = new StubStockRepository();
        ItemInfoProvider itemInfo = sku -> Optional.of(new ItemInfo("엔진오일 필터", ItemUnit.EA, "엔진", "오일필터", 60));
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L), itemInfo);

        StockDetail detail = service.getDetail("WH-SE-001", "HMC-EN-00214", "WH-SE-001");

        assertThat(detail.quantity()).isZero();
        assertThat(detail.safetyStock()).isEqualTo(60); // 마스터 기본 안전재고 fallback
    }

    @Test
    void getDetail_재고행없고_Item마스터에도_없으면_StockNotFoundException() {
        StubStockRepository stockRepository = new StubStockRepository();
        ItemInfoProvider itemInfo = sku -> Optional.empty(); // 통합 활성(기본) + 마스터에 없음
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L), itemInfo);

        assertThatThrownBy(() -> service.getDetail("WH-SE-001", "NO-SUCH", "WH-SE-001"))
                .isInstanceOf(StockNotFoundException.class);
    }

    @Test
    void getDetail_재고행없고_Item호출이_실패하면_예외를_전파한다() {
        StubStockRepository stockRepository = new StubStockRepository();
        ItemInfoProvider itemInfo = sku -> {
            throw new ItemServiceUnavailableException("fail", new RuntimeException("down"));
        };
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L), itemInfo);

        assertThatThrownBy(() -> service.getDetail("WH-SE-001", "HMC-EN-00214", "WH-SE-001"))
                .isInstanceOf(ItemServiceUnavailableException.class);
    }

    @Test
    void getStockQuantities_창고가_있으면_재고수량_리스트를_반환한다() {
        StubStockRepository stockRepository = new StubStockRepository();
        stockRepository.quantities = List.of(
                new StockQuantity("HMC-EN-00214", 120, 50),
                new StockQuantity("HMC-BR-00788", 30, 40));
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(2L), ITEM_NOOP);

        List<StockQuantity> result = service.getStockQuantities(
                WarehouseStockQuery.of("WH-SE-001", "HMC-EN-00214,HMC-BR-00788"));

        assertThat(result).extracting(StockQuantity::sku).containsExactly("HMC-EN-00214", "HMC-BR-00788");
        assertThat(stockRepository.quantitiesWarehouseCode).isEqualTo("WH-SE-001");
        assertThat(stockRepository.quantitiesSkus).containsExactly("HMC-EN-00214", "HMC-BR-00788");
    }

    @Test
    void getStockQuantities_창고가_없으면_WarehouseNotFoundException을_던지고_조회하지_않는다() {
        StubStockRepository stockRepository = new StubStockRepository();
        StockService service = new StockService(stockRepository, new StubWarehouseRepository(null), ITEM_NOOP);

        assertThatThrownBy(() -> service.getStockQuantities(WarehouseStockQuery.of("NOPE", "HMC-EN-00214")))
                .isInstanceOf(WarehouseNotFoundException.class);
        assertThat(stockRepository.quantitiesQueried).isFalse();
    }

    private static final class StubStockRepository implements StockRepository {
        private boolean exists = false;
        private Stock saved;
        private Long savedWarehouseId;
        private StockSearchQuery searchArg;
        private StockDetail detailResult;
        private boolean detailQueried = false;
        private List<StockQuantity> quantities = List.of();
        private boolean quantitiesQueried = false;
        private String quantitiesWarehouseCode;
        private List<String> quantitiesSkus;

        @Override
        public StockSummaryPage search(StockSearchQuery query) {
            this.searchArg = query;
            return new StockSummaryPage(List.of(), query.page(), query.size(), 0, 0);
        }

        @Override
        public Optional<StockDetail> findDetailByWarehouseCodeAndSku(String warehouseCode, String sku) {
            this.detailQueried = true;
            return Optional.ofNullable(detailResult);
        }

        @Override
        public List<StockQuantity> findQuantitiesByWarehouseCodeAndSkus(String warehouseCode, List<String> skus) {
            this.quantitiesQueried = true;
            this.quantitiesWarehouseCode = warehouseCode;
            this.quantitiesSkus = skus;
            return quantities;
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

        @Override
        public List<StockSkuRow> findSkuWarehouseStocks(String sku, List<String> warehouseCodes) {
            return List.of();
        }

        @Override
        public StockStatusCount countByStatus(List<String> warehouseCodes) {
            return new StockStatusCount(0, 0, 0);
        }

        @Override
        public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
            return Optional.empty();
        }

        @Override
        public Optional<Stock> findBySkuAndWarehouseIdForUpdate(String sku, Long warehouseId) {
            return Optional.empty();
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
            return warehouseId != null;
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
