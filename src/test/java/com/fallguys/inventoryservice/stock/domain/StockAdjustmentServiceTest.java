package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.stock.domain.command.AdjustStockCommand;
import com.fallguys.inventoryservice.stock.domain.exception.InsufficientStockException;
import com.fallguys.inventoryservice.stock.domain.exception.NoStockChangeException;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.StockAdjustmentResult;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

class StockAdjustmentServiceTest {

    private static AdjustStockCommand command(AdjustmentType type, int quantity) {
        return new AdjustStockCommand("HMC-EN-00214", "WH-SE-002", type, quantity,
                MovementReason.DAMAGE, "메모", "HMC0001");
    }

    @Test
    void DECREASE는_재고를_차감하고_이동이력을_저장한_결과를_반환한다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", 2L, 51, 50);
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockAdjustmentService service = new StockAdjustmentService(stockRepo, movementRepo);

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
    }

    @Test
    void ADJUST는_실측값으로_보정한다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", 2L, 51, 50);
        StockAdjustmentService service = new StockAdjustmentService(stockRepo, new StubMovementRepository());

        StockAdjustmentResult result = service.adjust(command(AdjustmentType.ADJUST, 40));

        assertThat(result.delta()).isEqualTo(-11);
        assertThat(result.currentQuantity()).isEqualTo(40);
    }

    @Test
    void 재고가_없으면_StockNotFoundException() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = null;
        StockAdjustmentService service = new StockAdjustmentService(stockRepo, new StubMovementRepository());

        assertThatThrownBy(() -> service.adjust(command(AdjustmentType.DECREASE, 3)))
                .isInstanceOf(StockNotFoundException.class);
    }

    @Test
    void 차감이_가용재고_초과면_InsufficientStockException이고_이력을_저장하지_않는다() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", 2L, 51, 50);
        StubMovementRepository movementRepo = new StubMovementRepository();
        StockAdjustmentService service = new StockAdjustmentService(stockRepo, movementRepo);

        assertThatThrownBy(() -> service.adjust(command(AdjustmentType.DECREASE, 100)))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(movementRepo.saved).isNull();
    }

    @Test
    void 변동이_없으면_NoStockChangeException() {
        StubStockRepository stockRepo = new StubStockRepository();
        stockRepo.stock = Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", 2L, 50, 50);
        StockAdjustmentService service = new StockAdjustmentService(stockRepo, new StubMovementRepository());

        assertThatThrownBy(() -> service.adjust(command(AdjustmentType.ADJUST, 50)))
                .isInstanceOf(NoStockChangeException.class);
    }

    private static final class StubStockRepository implements StockRepository {
        private Stock stock;
        private Integer savedQuantity;

        @Override
        public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
            return Optional.ofNullable(stock);
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
        private StockMovement saved;

        @Override
        public StockMovement save(StockMovement movement) {
            this.saved = movement;
            return StockMovement.of(88231L, movement.getSku(), movement.getWarehouseId(), movement.getDelta(),
                    movement.getType(), movement.getReason(), movement.getSourceRef(), movement.getSourceLineNo(),
                    movement.getStockAfter(), movement.getMemo(), movement.getExecutorEmpNo(),
                    Instant.parse("2026-05-28T14:35:00Z"));
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
}
