package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.config.JpaAuditingConfig;
import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.MovementReason;
import com.fallguys.inventoryservice.stock.domain.MovementType;
import com.fallguys.inventoryservice.stock.domain.StockMovement;
import com.fallguys.inventoryservice.stock.domain.query.InboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSortField;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummary;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.OutboundMovement;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import({StockMovementRepositoryAdapter.class, JpaAuditingConfig.class})
class StockMovementRepositoryAdapterTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate WIDE_FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate WIDE_TO = LocalDate.of(2026, 6, 30);

    @Autowired
    private StockMovementRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void seed() {
        insertWarehouse(2L, "WH-SE-001", "서울 1창고");
        insertWarehouse(5L, "HQ-001", "본사 중앙창고");
        // 부품명 조인용 stock 스냅샷
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L);
        insertStock("HMC-EN-00214", "엔진오일 필터", 5L);
        insertStock("HMC-BR-00788", "브레이크 패드", 2L);
        // 이동 이력 (삽입 순서가 동일시각 tie-break의 id 순서가 된다). 부품명·단위는 이동 이력 자체 스냅샷.
        insertMovement("HMC-EN-00214", "엔진오일 필터", 2L, 40, "INBOUND", null, "SO-1", 1, 40, instant("2026-05-15"));
        insertMovement("HMC-EN-00214", "엔진오일 필터", 5L, -40, "OUTBOUND", null, "SO-1", 1, 460, instant("2026-05-15"));
        insertMovement("HMC-BR-00788", "브레이크 패드", 2L, 60, "INBOUND", null, "SO-2", 1, 60, instant("2026-05-20"));
        insertMovement("HMC-EN-00214", "엔진오일 필터", 2L, 30, "INBOUND", null, "SO-3", 1, 70, instant("2026-06-03"));
        insertMovement("HMC-EN-00214", "엔진오일 필터", 2L, -5, "ADJUST", "DAMAGE", null, null, 65, instant("2026-06-04"));
    }

    @Test
    void 기간내_필터없는_조회는_전체와_페이지메타를_반환한다() {
        MovementSummaryPage page = adapter.search(query(null, List.of(), null,
                WIDE_FROM, WIDE_TO, MovementSortField.OCCURRED_AT, SortDirection.DESC, 1, 20));

        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.content()).hasSize(5);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(1);
    }

    @Test
    void warehouseCodes로_특정_창고_이력만_조회한다() {
        MovementSummaryPage page = adapter.search(query(null, List.of("WH-SE-001"), null,
                WIDE_FROM, WIDE_TO, MovementSortField.OCCURRED_AT, SortDirection.DESC, 1, 20));

        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.content()).extracting(MovementSummary::warehouseCode).containsOnly("WH-SE-001");
    }

    @Test
    void type_OUTBOUND만_조회한다() {
        MovementSummaryPage page = adapter.search(query(null, List.of(), MovementType.OUTBOUND,
                WIDE_FROM, WIDE_TO, MovementSortField.OCCURRED_AT, SortDirection.DESC, 1, 20));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).extracting(MovementSummary::type).containsExactly(MovementType.OUTBOUND);
    }

    @Test
    void keyword_부품명_부분일치로_조회한다() {
        MovementSummaryPage page = adapter.search(query("엔진", List.of(), null,
                WIDE_FROM, WIDE_TO, MovementSortField.OCCURRED_AT, SortDirection.DESC, 1, 20));

        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.content()).extracting(MovementSummary::sku).containsOnly("HMC-EN-00214");
    }

    @Test
    void keyword_SKU_부분일치로_조회한다() {
        MovementSummaryPage page = adapter.search(query("br-00788", List.of(), null,
                WIDE_FROM, WIDE_TO, MovementSortField.OCCURRED_AT, SortDirection.DESC, 1, 20));

        assertThat(page.content()).extracting(MovementSummary::sku).containsExactly("HMC-BR-00788");
    }

    @Test
    void 기간으로_조회_구간을_제한한다() {
        MovementSummaryPage page = adapter.search(query(null, List.of(), null,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30),
                MovementSortField.OCCURRED_AT, SortDirection.DESC, 1, 20));

        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void 종료일이_포함되도록_상한은_다음날_00시_미만이다() {
        MovementSummaryPage page = adapter.search(query(null, List.of(), null,
                LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 4),
                MovementSortField.OCCURRED_AT, SortDirection.DESC, 1, 20));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).extracting(MovementSummary::type).containsExactly(MovementType.ADJUST);
    }

    @Test
    void sort_occurredAt_desc는_최신순이며_동일시각은_id오름차순이다() {
        MovementSummaryPage page = adapter.search(query(null, List.of(), null,
                WIDE_FROM, WIDE_TO, MovementSortField.OCCURRED_AT, SortDirection.DESC, 1, 20));

        // 06-04(-5), 06-03(30), 05-20(60), 05-15(40, -40: 동일시각 id asc)
        assertThat(page.content()).extracting(MovementSummary::delta).containsExactly(-5, 30, 60, 40, -40);
    }

    @Test
    void sort_delta_asc는_변동량_오름차순이다() {
        MovementSummaryPage page = adapter.search(query(null, List.of(), null,
                WIDE_FROM, WIDE_TO, MovementSortField.DELTA, SortDirection.ASC, 1, 20));

        assertThat(page.content()).extracting(MovementSummary::delta).containsExactly(-40, -5, 30, 40, 60);
    }

    @Test
    void 페이지네이션은_size로_나누고_totalPages를_계산한다() {
        MovementSummaryPage page1 = adapter.search(query(null, List.of(), null,
                WIDE_FROM, WIDE_TO, MovementSortField.DELTA, SortDirection.ASC, 1, 2));

        assertThat(page1.content()).hasSize(2);
        assertThat(page1.totalElements()).isEqualTo(5);
        assertThat(page1.totalPages()).isEqualTo(3);
        assertThat(page1.content()).extracting(MovementSummary::delta).containsExactly(-40, -5);
    }

    @Test
    void 부품명은_stock_스냅샷에서_조인된다() {
        MovementSummaryPage page = adapter.search(query("br-00788", List.of(), null,
                WIDE_FROM, WIDE_TO, MovementSortField.OCCURRED_AT, SortDirection.DESC, 1, 20));

        assertThat(page.content().get(0).itemName()).isEqualTo("브레이크 패드");
    }

    @Test
    void findRecentBySku_전체창고는_sku의_최근이력을_시각내림차순으로_반환한다() {
        // HMC-EN-00214: 06-04(ADJUST -5), 06-03(IN 30), 05-15(OUT -40, IN 40: 동일시각 id 내림차순)
        List<MovementHistory> history = adapter.findRecentBySku("HMC-EN-00214", List.of(), 5);

        assertThat(history).extracting(MovementHistory::delta).containsExactly(-5, 30, -40, 40);
    }

    @Test
    void findRecentBySku_limit으로_건수를_제한한다() {
        List<MovementHistory> history = adapter.findRecentBySku("HMC-EN-00214", List.of(), 2);

        assertThat(history).extracting(MovementHistory::delta).containsExactly(-5, 30);
    }

    @Test
    void findRecentBySku_창고코드_필터는_해당_창고_이력만_반환한다() {
        // HMC-EN-00214는 WH-SE-001(IN·IN·ADJUST)과 HQ-001(OUT)에 있다 → HQ-001 필터 시 OUTBOUND만.
        List<MovementHistory> history = adapter.findRecentBySku("HMC-EN-00214", List.of("HQ-001"), 5);

        assertThat(history).extracting(MovementHistory::type).containsExactly(MovementType.OUTBOUND);
    }

    @Test
    void countRecent_기준시각_이후_이동을_센다() {
        Instant since = LocalDate.of(2026, 6, 3).atStartOfDay(ZONE).toInstant();

        long count = adapter.countRecent(List.of(), since);

        assertThat(count).isEqualTo(2); // 06-03, 06-04
    }

    @Test
    void countRecent_창고코드_필터는_해당_창고_이동만_센다() {
        Instant since = LocalDate.of(2026, 5, 1).atStartOfDay(ZONE).toInstant();

        long count = adapter.countRecent(List.of("HQ-001"), since);

        assertThat(count).isEqualTo(1); // HQ-001 OUTBOUND 1건
    }

    @Test
    void save는_이동이력을_저장하고_식별자와_발생시각을_채운다() {
        StockMovement movement = StockMovement.createAdjustment(
                "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, -3, MovementType.DECREASE, MovementReason.DAMAGE, 48,
                "파손", "HMC0001", "홍길동");

        StockMovement saved = adapter.save(movement);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPerformedAt()).isNotNull();
        assertThat(saved.getDelta()).isEqualTo(-3);
        assertThat(saved.getType()).isEqualTo(MovementType.DECREASE);
        assertThat(saved.getReason()).isEqualTo(MovementReason.DAMAGE);
    }

    @Test
    void findInboundBySourceRefAndWarehouseCode_해당창고_INBOUND만_반환한다() {
        // SO-1: WH-SE-001 INBOUND(+40, stock_after 40), HQ-001 OUTBOUND(-40) → WH-SE-001 조회 시 INBOUND만.
        List<InboundMovement> rows = adapter.findInboundBySourceRefAndWarehouseCode("SO-1", "WH-SE-001");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).sku()).isEqualTo("HMC-EN-00214");
        assertThat(rows.get(0).delta()).isEqualTo(40);
        assertThat(rows.get(0).currentQuantity()).isEqualTo(40); // stock_after
    }

    @Test
    void findInboundBySourceRefAndWarehouseCode_INBOUND이_없으면_빈리스트다() {
        // HQ-001의 SO-1은 OUTBOUND뿐이라 type 필터에서 제외된다.
        assertThat(adapter.findInboundBySourceRefAndWarehouseCode("SO-1", "HQ-001")).isEmpty();
        assertThat(adapter.findInboundBySourceRefAndWarehouseCode("NO-SUCH", "WH-SE-001")).isEmpty();
    }

    @Test
    void findOutboundBySourceRefAndWarehouseCode_해당창고_OUTBOUND만_반환한다() {
        // SO-1: WH-SE-001 INBOUND(+40), HQ-001 OUTBOUND(-40, stock_after 460) → HQ-001 조회 시 OUTBOUND만.
        List<OutboundMovement> rows = adapter.findOutboundBySourceRefAndWarehouseCode("SO-1", "HQ-001");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).sku()).isEqualTo("HMC-EN-00214");
        assertThat(rows.get(0).delta()).isEqualTo(-40);
        assertThat(rows.get(0).currentQuantity()).isEqualTo(460); // stock_after
    }

    @Test
    void findOutboundBySourceRefAndWarehouseCode_OUTBOUND이_없으면_빈리스트다() {
        // WH-SE-001의 SO-1은 INBOUND뿐이라 type 필터에서 제외된다.
        assertThat(adapter.findOutboundBySourceRefAndWarehouseCode("SO-1", "WH-SE-001")).isEmpty();
        assertThat(adapter.findOutboundBySourceRefAndWarehouseCode("NO-SUCH", "HQ-001")).isEmpty();
    }

    private static MovementSearchQuery query(String keyword, List<String> warehouseCodes, MovementType type,
            LocalDate from, LocalDate to, MovementSortField field, SortDirection direction, int page, int size) {
        return new MovementSearchQuery(keyword, warehouseCodes, type, from, to, field, direction, page, size);
    }

    private static Instant instant(String date) {
        return LocalDate.parse(date).atTime(10, 0).atZone(ZONE).toInstant();
    }

    private void insertMovement(String sku, String itemName, long warehouseId, int delta, String type, String reason,
            String sourceRef, Integer sourceLineNo, int stockAfter, Instant performedAt) {
        // 오일류는 L, 그 외는 EA로 단위를 스냅샷한다(테스트 시드용 단순 파생).
        String itemUnit = itemName.contains("오일") && !itemName.contains("필터") ? "L" : "EA";
        entityManager().createNativeQuery("""
                        INSERT INTO stock_movement
                            (sku, item_name, item_unit, warehouse_id, delta, type, reason, source_ref, source_line_no,
                             stock_after, note, executor_emp_no, executor_name, performed_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, sku)
                .setParameter(2, itemName)
                .setParameter(3, itemUnit)
                .setParameter(4, warehouseId)
                .setParameter(5, delta)
                .setParameter(6, type)
                .setParameter(7, reason)
                .setParameter(8, sourceRef)
                .setParameter(9, sourceLineNo)
                .setParameter(10, stockAfter)
                .setParameter(11, null)
                .setParameter(12, "HMC0001")
                .setParameter(13, "홍길동")
                .setParameter(14, performedAt)
                .executeUpdate();
    }

    private void insertStock(String sku, String itemName, long warehouseId) {
        // 오일류는 L, 그 외는 EA로 단위를 스냅샷한다(테스트 시드용 단순 파생).
        String itemUnit = itemName.contains("오일") && !itemName.contains("필터") ? "L" : "EA";
        entityManager().createNativeQuery("""
                        INSERT INTO stock
                            (sku, item_name, item_unit, warehouse_id, current_stock, safety_stock,
                             created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, sku)
                .setParameter(2, itemName)
                .setParameter(3, itemUnit)
                .setParameter(4, warehouseId)
                .setParameter(5, 100)
                .setParameter(6, 50)
                .setParameter(7, Instant.parse("2026-05-10T00:00:00Z"))
                .setParameter(8, Instant.parse("2026-05-10T00:00:00Z"))
                .setParameter(9, 0L)
                .executeUpdate();
    }

    private void insertWarehouse(long id, String code, String name) {
        entityManager().createNativeQuery("""
                        INSERT INTO warehouse
                            (id, code, name, type, branch_id, address, active,
                             created_by, updated_by, created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, id)
                .setParameter(2, code)
                .setParameter(3, name)
                .setParameter(4, "HQ")
                .setParameter(5, null)
                .setParameter(6, "주소")
                .setParameter(7, true)
                .setParameter(8, "EMP-001")
                .setParameter(9, "EMP-001")
                .setParameter(10, Instant.parse("2024-01-01T00:00:00Z"))
                .setParameter(11, Instant.parse("2024-01-01T00:00:00Z"))
                .setParameter(12, 0L)
                .executeUpdate();
    }

    private EntityManager entityManager() {
        return testEntityManager.getEntityManager();
    }
}
