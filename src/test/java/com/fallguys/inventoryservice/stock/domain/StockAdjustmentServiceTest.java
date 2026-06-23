package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.stock.domain.command.AdjustStockCommand;
import com.fallguys.inventoryservice.stock.domain.exception.InsufficientStockException;
import com.fallguys.inventoryservice.stock.domain.exception.ItemInactiveException;
import com.fallguys.inventoryservice.stock.domain.exception.NoStockChangeException;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.StockAdjustmentResult;
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
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

class StockAdjustmentServiceTest {

    private static AdjustStockCommand command(AdjustmentType type, int quantity) {
        return new AdjustStockCommand("HMC-EN-00214", "WH-SE-002", type, quantity,
                MovementReason.DAMAGE, "메모", "HMC0001", "홍길동");
    }

    @Test
    void DECREASE는_재고를_차감하고_이동이력을_저장한_결과를_반환한다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 51, 50);
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockAdjustmentService service = new StockAdjustmentService(stockRepo, movementRepo, new StubWarehouseRepository());

        StockAdjustmentResult result = service.adjust(command(AdjustmentType.DECREASE, 3));

        assertThat(result.previousQuantity()).isEqualTo(51);
        assertThat(result.delta()).isEqualTo(-3);
        assertThat(result.currentQuantity()).isEqualTo(48);
        assertThat(result.stockId()).isEqualTo(1001L);
        assertThat(result.movementId()).isEqualTo(88231L);
        assertThat(result.occurredAt()).isNotNull();
        assertThat(stockRepo.savedQuantity).isEqualTo(48);
        assertThat(movementRepo.saved.getDelta()).isEqualTo(-3);
        assertThat(movementRepo.saved.getType()).isEqualTo(MovementType.DECREASE);
        assertThat(movementRepo.saved.getStockAfter()).isEqualTo(48);
        assertThat(movementRepo.saved.getExecutorEmpNo()).isEqualTo("HMC0001");
        assertThat(movementRepo.saved.getExecutorName()).isEqualTo("홍길동");
        assertThat(movementRepo.saved.getItemName()).isEqualTo("엔진오일 필터");
        assertThat(movementRepo.saved.getItemUnit()).isEqualTo(ItemUnit.EA);
    }

    @Test
    void ADJUST는_실측값으로_보정한다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 51, 50);
        StockAdjustmentService service = new StockAdjustmentService(stockRepo, new StubMovementRepository(), new StubWarehouseRepository());

        StockAdjustmentResult result = service.adjust(command(AdjustmentType.ADJUST, 40));

        assertThat(result.delta()).isEqualTo(-11);
        assertThat(result.currentQuantity()).isEqualTo(40);
    }

    @Test
    void 재고가_없으면_StockNotFoundException() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = null;
        StockAdjustmentService service = new StockAdjustmentService(stockRepo, new StubMovementRepository(), new StubWarehouseRepository());

        assertThatThrownBy(() -> service.adjust(command(AdjustmentType.DECREASE, 3)))
                .isInstanceOf(StockNotFoundException.class);
    }

    @Test
    void 차감이_가용재고_초과면_InsufficientStockException이고_이력을_저장하지_않는다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 51, 50);
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockAdjustmentService service = new StockAdjustmentService(stockRepo, movementRepo, new StubWarehouseRepository());

        assertThatThrownBy(() -> service.adjust(command(AdjustmentType.DECREASE, 100)))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(movementRepo.saved).isNull();
    }

    @Test
    void 변동이_없으면_NoStockChangeException() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 50, 50);
        StockAdjustmentService service = new StockAdjustmentService(stockRepo, new StubMovementRepository(), new StubWarehouseRepository());

        assertThatThrownBy(() -> service.adjust(command(AdjustmentType.ADJUST, 50)))
                .isInstanceOf(NoStockChangeException.class);
    }

    @Test
    void 비활성_창고면_WarehouseInactiveException이고_저장하지_않는다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 51, 50);
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockAdjustmentService service =
                new StockAdjustmentService(stockRepo, movementRepo, new StubWarehouseRepository(false));

        assertThatThrownBy(() -> service.adjust(command(AdjustmentType.DECREASE, 3)))
                .isInstanceOf(WarehouseInactiveException.class);
        assertThat(stockRepo.savedQuantity).isNull();
        assertThat(movementRepo.saved).isNull();
    }

    @Test
    void 비활성_아이템이면_ItemInactiveException이고_저장하지_않는다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 51, 50, false);
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockAdjustmentService service =
                new StockAdjustmentService(stockRepo, movementRepo, new StubWarehouseRepository());

        assertThatThrownBy(() -> service.adjust(command(AdjustmentType.DECREASE, 3)))
                .isInstanceOf(ItemInactiveException.class);
        assertThat(stockRepo.savedQuantity).isNull();
        assertThat(movementRepo.saved).isNull();
    }

    private static final class StubStockRepository implements StockRepository {
        private Stock stock;
        private Integer savedQuantity;

        @Override
        public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
            return Optional.ofNullable(stock);
        }

        @Override
        public Optional<Stock> findBySkuAndWarehouseIdForUpdate(String sku, Long warehouseId) {
            return Optional.ofNullable(stock);
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

        @Override
        public Long save(Stock stock) {
            this.savedQuantity = stock.getQuantity();
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
            return new StockStatusCount(0, 0);
        }
    }

    private static final class StubMovementRepository implements StockMovementRepository {
        private StockMovement saved;

        @Override
        public StockMovement save(StockMovement movement) {
            this.saved = movement;
            return StockMovement.of(88231L, movement.getSku(), movement.getItemName(), movement.getItemUnit(),
                    movement.getWarehouseId(), movement.getDelta(), movement.getType(), movement.getReason(),
                    movement.getSourceRef(), movement.getSourceLineNo(), movement.getStockAfter(),
                    movement.getNote(), movement.getExecutorEmpNo(), movement.getExecutorName(),
                    Instant.parse("2026-05-28T14:35:00Z"));
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
        private final boolean active;

        private StubWarehouseRepository() {
            this(true);
        }

        private StubWarehouseRepository(boolean active) {
            this.active = active;
        }

        @Override
        public Optional<WarehouseSummaryForEdit> findForEditByCode(String code) {
            return Optional.of(new WarehouseSummaryForEdit(
                    2L, code, "창고", WarehouseType.DEALER, 3L, "지점", "주소", active,
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
            return true;
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
