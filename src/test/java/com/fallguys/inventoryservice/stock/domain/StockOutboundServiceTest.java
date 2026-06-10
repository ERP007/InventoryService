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

import com.fallguys.inventoryservice.stock.domain.command.OutboundCommand;
import com.fallguys.inventoryservice.stock.domain.command.OutboundLine;
import com.fallguys.inventoryservice.stock.domain.exception.InsufficientStockException;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.InboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.OutboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.OutboundResult;
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

class StockOutboundServiceTest {

    private static OutboundCommand command(List<OutboundLine> lines) {
        return new OutboundCommand("SO-2026-0034", "WH-SE-002", lines, "HMC1001", "김본사");
    }

    @Test
    void 정상_다중라인을_차감하고_OUTBOUND이력을_라인수만큼_남긴다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.put(Stock.of(11L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 100, 50));
        stockRepo.put(Stock.of(12L, "HMC-BR-00788", "브레이크 패드", ItemUnit.SET, 2L, 40, 40));
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockOutboundService service = new StockOutboundService(stockRepo, movementRepo, new StubWarehouseRepository(2L, true));

        OutboundResult result = service.outbound(command(List.of(
                new OutboundLine("HMC-EN-00214", 30, 1),
                new OutboundLine("HMC-BR-00788", 10, 2))));

        assertThat(result.movements()).hasSize(2);
        assertThat(stockRepo.get("HMC-EN-00214").getQuantity()).isEqualTo(70);
        assertThat(stockRepo.get("HMC-BR-00788").getQuantity()).isEqualTo(30);

        StockMovement m1 = movementRepo.saved.get(0);
        assertThat(m1.getType()).isEqualTo(MovementType.OUTBOUND);
        assertThat(m1.getReason()).isNull();
        assertThat(m1.getDelta()).isEqualTo(-30);
        assertThat(m1.getStockAfter()).isEqualTo(70);
        assertThat(m1.getExecutorEmpNo()).isEqualTo("HMC1001");
        assertThat(m1.getExecutorName()).isEqualTo("김본사");
        assertThat(m1.getSourceRef()).isEqualTo("SO-2026-0034");
        assertThat(m1.getSourceLineNo()).isEqualTo(1);

        OutboundMovement r1 = result.movements().get(0);
        assertThat(r1.sku()).isEqualTo("HMC-EN-00214");
        assertThat(r1.delta()).isEqualTo(-30);
        assertThat(r1.currentQuantity()).isEqualTo(70);
        // 출고는 비관락 finder로만 재고를 읽는다(비잠금 finder 미사용).
        assertThat(stockRepo.forUpdateCalls).isEqualTo(2);
        assertThat(stockRepo.nonLockingCalls).isZero();
    }

    @Test
    void 멱등_같은sourceRef로_이미_처리됐으면_replay하고_재차감하지_않는다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.put(Stock.of(11L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 100, 50));
        StubMovementRepository movementRepo = new StubMovementRepository();
        movementRepo.alreadyProcessed = List.of(new OutboundMovement(88250L, "HMC-EN-00214", -20, 80));
        StockOutboundService service = new StockOutboundService(stockRepo, movementRepo, new StubWarehouseRepository(2L, true));

        OutboundResult result = service.outbound(command(List.of(new OutboundLine("HMC-EN-00214", 20, 1))));

        assertThat(result.movements()).extracting(OutboundMovement::movementId).containsExactly(88250L);
        assertThat(stockRepo.get("HMC-EN-00214").getQuantity()).isEqualTo(100); // 재차감 안 됨
        assertThat(movementRepo.saved).isEmpty();                                // 신규 저장 안 됨
        assertThat(stockRepo.forUpdateCalls).isZero();                           // 재고 조회·잠금조차 안 함
    }

    @Test
    void 창고가_없으면_WarehouseNotFoundException() {
        StockOutboundService service = new StockOutboundService(
                new StubStockRepository(), new StubMovementRepository(), new StubWarehouseRepository(null, true));

        assertThatThrownBy(() -> service.outbound(command(List.of(new OutboundLine("HMC-EN-00214", 20, 1)))))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void 비활성_창고면_WarehouseInactiveException() {
        StockOutboundService service = new StockOutboundService(
                new StubStockRepository(), new StubMovementRepository(), new StubWarehouseRepository(2L, false));

        assertThatThrownBy(() -> service.outbound(command(List.of(new OutboundLine("HMC-EN-00214", 20, 1)))))
                .isInstanceOf(WarehouseInactiveException.class);
    }

    @Test
    void 재고행이_없으면_StockNotFoundException_이고_신규생성하지_않는다() {
        StubStockRepository stockRepo = new StubStockRepository(); // 재고 없음 → 신규 생성하지 않고 404
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockOutboundService service = new StockOutboundService(stockRepo, movementRepo, new StubWarehouseRepository(2L, true));

        assertThatThrownBy(() -> service.outbound(command(List.of(new OutboundLine("HMC-EN-00214", 20, 1)))))
                .isInstanceOf(StockNotFoundException.class);
        assertThat(movementRepo.saved).isEmpty();
    }

    @Test
    void 가용재고_부족이면_InsufficientStockException_이고_차감되지_않는다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.put(Stock.of(11L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 10, 50));
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockOutboundService service = new StockOutboundService(stockRepo, movementRepo, new StubWarehouseRepository(2L, true));

        assertThatThrownBy(() -> service.outbound(command(List.of(new OutboundLine("HMC-EN-00214", 20, 1)))))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(stockRepo.get("HMC-EN-00214").getQuantity()).isEqualTo(10); // 도메인이 검증 실패 시 현재고 보존
        assertThat(movementRepo.saved).isEmpty();
    }

    // ---- stubs ----

    private static final class StubStockRepository implements StockRepository {
        private final Map<String, Stock> stocks = new HashMap<>();
        private int forUpdateCalls;
        private int nonLockingCalls;

        void put(Stock stock) {
            stocks.put(stock.getSku(), stock);
        }

        Stock get(String sku) {
            return stocks.get(sku);
        }

        @Override
        public Optional<Stock> findBySkuAndWarehouseIdForUpdate(String sku, Long warehouseId) {
            forUpdateCalls++;
            return Optional.ofNullable(stocks.get(sku));
        }

        @Override
        public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
            nonLockingCalls++;
            return Optional.ofNullable(stocks.get(sku));
        }

        @Override
        public Long save(Stock stock) {
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
        private List<OutboundMovement> alreadyProcessed = List.of();
        private final List<StockMovement> saved = new ArrayList<>();
        private long nextId = 88250L;

        @Override
        public List<OutboundMovement> findOutboundBySourceRefAndWarehouseCode(String sourceRef, String warehouseCode) {
            return alreadyProcessed;
        }

        @Override
        public StockMovement save(StockMovement movement) {
            StockMovement persisted = StockMovement.of(
                    nextId++, movement.getSku(), movement.getItemName(), movement.getItemUnit(),
                    movement.getWarehouseId(), movement.getDelta(), movement.getType(), movement.getReason(),
                    movement.getSourceRef(), movement.getSourceLineNo(), movement.getStockAfter(),
                    movement.getNote(), movement.getExecutorEmpNo(), movement.getExecutorName(),
                    Instant.parse("2026-06-10T00:00:00Z"));
            saved.add(persisted);
            return persisted;
        }

        @Override
        public List<InboundMovement> findInboundBySourceRefAndWarehouseCode(String sourceRef, String warehouseCode) {
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
