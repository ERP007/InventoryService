package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.stock.domain.command.InboundCommand;
import com.fallguys.inventoryservice.stock.domain.command.InboundLine;
import com.fallguys.inventoryservice.stock.domain.exception.ItemNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.InboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.InboundResult;
import com.fallguys.inventoryservice.stock.domain.query.ItemInfo;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;
import com.fallguys.inventoryservice.warehouse.domain.Warehouse;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseRepository;
import com.fallguys.inventoryservice.warehouse.domain.command.ChangeWarehouseActiveCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseInactiveException;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

class StockInboundServiceTest {

    private static InboundCommand command(List<InboundLine> lines) {
        return new InboundCommand("PO-2026-0012", "WH-SE-001", lines, "HMC1001", "김본사");
    }

    @Test
    void 정상_다중라인을_증가시키고_INBOUND이력을_라인수만큼_남긴다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.put(Stock.of(11L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 100, 50));
        stockRepo.put(Stock.of(12L, "HMC-BR-00788", "브레이크 패드", ItemUnit.SET, 2L, 40, 40));
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockInboundService service = new StockInboundService(stockRepo, movementRepo, new StubWarehouseRepository(2L, true), sku -> Optional.empty());

        InboundResult result = service.inbound(command(List.of(
                new InboundLine("HMC-EN-00214", 30, 1),
                new InboundLine("HMC-BR-00788", 10, 2))));

        assertThat(result.movements()).hasSize(2);
        assertThat(stockRepo.get("HMC-EN-00214").getQuantity()).isEqualTo(130);
        assertThat(stockRepo.get("HMC-BR-00788").getQuantity()).isEqualTo(50);

        StockMovement m1 = movementRepo.saved.get(0);
        assertThat(m1.getType()).isEqualTo(MovementType.INBOUND);
        assertThat(m1.getReason()).isNull();
        assertThat(m1.getDelta()).isEqualTo(30);
        assertThat(m1.getStockAfter()).isEqualTo(130);
        assertThat(m1.getExecutorEmpNo()).isEqualTo("HMC1001");
        assertThat(m1.getExecutorName()).isEqualTo("김본사");
        assertThat(m1.getSourceRef()).isEqualTo("PO-2026-0012");
        assertThat(m1.getSourceLineNo()).isEqualTo(1);

        InboundMovement r1 = result.movements().get(0);
        assertThat(r1.sku()).isEqualTo("HMC-EN-00214");
        assertThat(r1.delta()).isEqualTo(30);
        assertThat(r1.currentQuantity()).isEqualTo(130);
    }

    @Test
    void 멱등_같은sourceRef로_이미_처리됐으면_replay하고_재증가하지_않는다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.put(Stock.of(11L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 100, 50));
        StubMovementRepository movementRepo = new StubMovementRepository();
        movementRepo.alreadyProcessed = List.of(new InboundMovement(88240L, "HMC-EN-00214", 30, 130));
        StockInboundService service = new StockInboundService(stockRepo, movementRepo, new StubWarehouseRepository(2L, true), sku -> Optional.empty());

        InboundResult result = service.inbound(command(List.of(new InboundLine("HMC-EN-00214", 30, 1))));

        assertThat(result.movements()).extracting(InboundMovement::movementId).containsExactly(88240L);
        assertThat(stockRepo.get("HMC-EN-00214").getQuantity()).isEqualTo(100); // 재증가 안 됨
        assertThat(movementRepo.saved).isEmpty();                                // 신규 저장 안 됨
    }

    @Test
    void 창고가_없으면_WarehouseNotFoundException() {
        StockInboundService service = new StockInboundService(
                new StubStockRepository(), new StubMovementRepository(), new StubWarehouseRepository(null, true),
                sku -> Optional.empty());

        assertThatThrownBy(() -> service.inbound(command(List.of(new InboundLine("HMC-EN-00214", 30, 1)))))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void 비활성_창고면_WarehouseInactiveException() {
        StockInboundService service = new StockInboundService(
                new StubStockRepository(), new StubMovementRepository(), new StubWarehouseRepository(2L, false),
                sku -> Optional.empty());

        assertThatThrownBy(() -> service.inbound(command(List.of(new InboundLine("HMC-EN-00214", 30, 1)))))
                .isInstanceOf(WarehouseInactiveException.class);
    }

    @Test
    void 재고행이_없으면_Item정보로_신규행을_생성하고_증가시킨다() {
        StubStockRepository stockRepo = new StubStockRepository(); // 재고 없음 → 신규 생성
        StubMovementRepository movementRepo = new StubMovementRepository();
        ItemInfoProvider itemInfo = sku -> Optional.of(new ItemInfo("엔진오일 필터", ItemUnit.EA, "엔진", "오일필터", 60));
        StockInboundService service = new StockInboundService(
                stockRepo, movementRepo, new StubWarehouseRepository(2L, true), itemInfo);

        InboundResult result = service.inbound(command(List.of(new InboundLine("HMC-EN-00214", 30, 1))));

        assertThat(result.movements()).extracting(InboundMovement::currentQuantity).containsExactly(30); // 0→30
        Stock created = stockRepo.lastSaved;
        assertThat(created.getItemName()).isEqualTo("엔진오일 필터");
        assertThat(created.getItemUnit()).isEqualTo(ItemUnit.EA);
        assertThat(created.getSafetyStock()).isEqualTo(60);   // Item 기본 안전재고
        assertThat(created.getQuantity()).isEqualTo(30);
    }

    @Test
    void 재고행도_Item정보도_없으면_ItemNotFoundException() {
        StubMovementRepository movementRepo = new StubMovementRepository();
        ItemInfoProvider itemInfo = sku -> Optional.empty(); // Item에도 없음(또는 통합 비활성)
        StockInboundService service = new StockInboundService(
                new StubStockRepository(), movementRepo, new StubWarehouseRepository(2L, true), itemInfo);

        assertThatThrownBy(() -> service.inbound(command(List.of(new InboundLine("NO-SUCH", 30, 1)))))
                .isInstanceOf(ItemNotFoundException.class);
        assertThat(movementRepo.saved).isEmpty();
    }

    // ---- stubs ----

    private static final class StubStockRepository implements StockRepository {
        private final Map<String, Stock> stocks = new HashMap<>();
        private Stock lastSaved;

        void put(Stock stock) {
            stocks.put(stock.getSku(), stock);
        }

        Stock get(String sku) {
            return stocks.get(sku);
        }

        @Override
        public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
            return Optional.ofNullable(stocks.get(sku));
        }

        @Override
        public Optional<Stock> findBySkuAndWarehouseIdForUpdate(String sku, Long warehouseId) {
            return Optional.ofNullable(stocks.get(sku));
        }

        @Override
        public Long save(Stock stock) {
            this.lastSaved = stock;
            return stock.getId();
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
        public Optional<StockCreateResult> findResultById(Long id) {
            return Optional.empty();
        }

        @Override
        public List<StockSkuRow> findSkuWarehouseStocks(String sku, List<String> warehouseCodes) {
            return List.of();
        }

        @Override
        public StockStatusCount countByStatus(List<String> warehouseCodes) {
            return new StockStatusCount(0, 0, 0);
        }
    }

    private static final class StubMovementRepository implements StockMovementRepository {
        private List<InboundMovement> alreadyProcessed = List.of();
        private final List<StockMovement> saved = new ArrayList<>();
        private long nextId = 88240L;

        @Override
        public List<InboundMovement> findInboundBySourceRefAndWarehouseCode(String sourceRef, String warehouseCode) {
            return alreadyProcessed;
        }

        @Override
        public List<com.fallguys.inventoryservice.stock.domain.query.OutboundMovement> findOutboundBySourceRefAndWarehouseCode(
                String sourceRef, String warehouseCode) {
            return List.of();
        }

        @Override
        public StockMovement save(StockMovement movement) {
            StockMovement persisted = StockMovement.of(
                    nextId++, movement.getSku(), movement.getItemName(), movement.getItemUnit(),
                    movement.getWarehouseId(), movement.getDelta(), movement.getType(), movement.getReason(),
                    movement.getSourceRef(), movement.getSourceLineNo(), movement.getStockAfter(),
                    movement.getNote(), movement.getExecutorEmpNo(), movement.getExecutorName(),
                    Instant.parse("2026-06-09T00:00:00Z"));
            saved.add(persisted);
            return persisted;
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
        public long countRecent(List<String> warehouseCodes, Instant since) {
            return 0;
        }
    }

    private static final class StubWarehouseRepository implements WarehouseRepository {
        private final Long warehouseId; // null이면 미존재
        private final boolean active;

        private StubWarehouseRepository(Long warehouseId, boolean active) {
            this.warehouseId = warehouseId;
            this.active = active;
        }

        @Override
        public Optional<WarehouseSummaryForEdit> findForEditByCode(String code) {
            if (warehouseId == null) {
                return Optional.empty();
            }
            return Optional.of(new WarehouseSummaryForEdit(
                    warehouseId, code, "창고", WarehouseType.DEALER, 3L, "지점", "주소", active,
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
