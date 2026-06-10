package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.StockMovement;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;

class StockMovementServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 7);

    @Test
    void search_BRANCH는_요청_창고필터를_무시하고_자기_창고로_강제한다() {
        StubRepo repo = new StubRepo();
        StockMovementService service = new StockMovementService(repo);
        MovementSearchQuery query =
                MovementSearchQuery.of(null, "WH-OTHER,HQ-001", null, null, null, null, null, null, TODAY);

        service.search(query, TenancyType.BRANCH, "WH-SE-001");

        assertThat(repo.searchArg.warehouseCodes()).containsExactly("WH-SE-001");
    }

    @Test
    void search_ADMIN은_요청_창고필터를_그대로_사용한다() {
        StubRepo repo = new StubRepo();
        StockMovementService service = new StockMovementService(repo);
        MovementSearchQuery query =
                MovementSearchQuery.of(null, "WH-SE-001,HQ-001", null, null, null, null, null, null, TODAY);

        service.search(query, TenancyType.ADMIN, null);

        assertThat(repo.searchArg.warehouseCodes()).containsExactly("WH-SE-001", "HQ-001");
    }

    @Test
    void search_HQ도_요청_창고필터를_그대로_사용한다() {
        StubRepo repo = new StubRepo();
        StockMovementService service = new StockMovementService(repo);
        MovementSearchQuery query =
                MovementSearchQuery.of(null, "WH-SE-002", null, null, null, null, null, null, TODAY);

        service.search(query, TenancyType.HQ, null);

        assertThat(repo.searchArg.warehouseCodes()).containsExactly("WH-SE-002");
    }

    private static final class StubRepo implements StockMovementRepository {
        private MovementSearchQuery searchArg;

        @Override
        public MovementSummaryPage search(MovementSearchQuery query) {
            this.searchArg = query;
            return new MovementSummaryPage(List.of(), query.page(), query.size(), 0, 0);
        }

        @Override
        public List<MovementHistory> findRecentBySku(String sku, List<String> warehouseCodes, int limit) {
            return List.of();
        }

        @Override
        public long countRecent(List<String> warehouseCodes, Instant since) {
            return 0;
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
