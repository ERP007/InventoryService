package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.shared.model.TenancyType;
import com.fallguys.inventoryservice.stock.domain.StockMovement;
import com.fallguys.inventoryservice.stock.domain.query.DailyActivity;
import com.fallguys.inventoryservice.stock.domain.query.DailyMovementCount;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.StockActivitySummary;

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

    @Test
    void getRecentActivity_ADMIN은_전사범위로_today기준_7일을_조회한다() {
        StubRepo repo = new StubRepo();
        StockMovementService service = new StockMovementService(repo);

        service.getRecentActivity(TenancyType.ADMIN, null, TODAY);

        assertThat(repo.dailyScope).isEmpty();
        assertThat(repo.dailyFrom).isEqualTo(LocalDate.of(2026, 6, 1)); // TODAY(06-07) 포함 7일
        assertThat(repo.dailyTo).isEqualTo(TODAY);
    }

    @Test
    void getRecentActivity_BRANCH는_자기창고로_범위를_강제한다() {
        StubRepo repo = new StubRepo();
        StockMovementService service = new StockMovementService(repo);

        service.getRecentActivity(TenancyType.BRANCH, "WH-SE-001", TODAY);

        assertThat(repo.dailyScope).containsExactly("WH-SE-001");
    }

    @Test
    void getRecentActivity_빈날도_0으로_채워_7개와_0합계를_반환한다() {
        StubRepo repo = new StubRepo();
        StockMovementService service = new StockMovementService(repo);

        StockActivitySummary summary = service.getRecentActivity(TenancyType.ADMIN, null, TODAY);

        assertThat(summary.from()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(summary.to()).isEqualTo(TODAY);
        assertThat(summary.days()).extracting(DailyActivity::date).containsExactly(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 6),
                LocalDate.of(2026, 6, 7));
        assertThat(summary.totalInbound()).isZero();
        assertThat(summary.totalOutbound()).isZero();
        assertThat(summary.totalAdjust()).isZero();
    }

    @Test
    void getRecentActivity_유형을_입고출고조정으로_접고_일자별로_합산한다() {
        StubRepo repo = new StubRepo();
        repo.dailyCounts = List.of(
                new DailyMovementCount(LocalDate.of(2026, 6, 3), MovementType.INBOUND, 5),
                new DailyMovementCount(LocalDate.of(2026, 6, 3), MovementType.OUTBOUND, 2),
                new DailyMovementCount(LocalDate.of(2026, 6, 3), MovementType.INCREASE, 1),
                new DailyMovementCount(LocalDate.of(2026, 6, 3), MovementType.DECREASE, 1),
                new DailyMovementCount(LocalDate.of(2026, 6, 3), MovementType.ADJUST, 3),
                new DailyMovementCount(LocalDate.of(2026, 6, 7), MovementType.INBOUND, 4));
        StockMovementService service = new StockMovementService(repo);

        StockActivitySummary summary = service.getRecentActivity(TenancyType.ADMIN, null, TODAY);

        DailyActivity june3 = summary.days().get(2); // 06-01[0], 06-02[1], 06-03[2]
        assertThat(june3.inbound()).isEqualTo(5);
        assertThat(june3.outbound()).isEqualTo(2);
        assertThat(june3.adjust()).isEqualTo(5); // INCREASE 1 + DECREASE 1 + ADJUST 3
        assertThat(summary.days().get(6).inbound()).isEqualTo(4); // 06-07
        assertThat(summary.totalInbound()).isEqualTo(9);
        assertThat(summary.totalOutbound()).isEqualTo(2);
        assertThat(summary.totalAdjust()).isEqualTo(5);
    }

    private static final class StubRepo implements StockMovementRepository {
        private MovementSearchQuery searchArg;
        private List<DailyMovementCount> dailyCounts = List.of();
        private List<String> dailyScope;
        private LocalDate dailyFrom;
        private LocalDate dailyTo;

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
        public List<DailyMovementCount> countDailyByType(List<String> warehouseCodes, LocalDate from, LocalDate to) {
            this.dailyScope = warehouseCodes;
            this.dailyFrom = from;
            this.dailyTo = to;
            return dailyCounts;
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
