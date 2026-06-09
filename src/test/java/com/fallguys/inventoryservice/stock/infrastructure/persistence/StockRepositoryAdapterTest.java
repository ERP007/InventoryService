package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.config.JpaAuditingConfig;
import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.stock.domain.AdjustmentType;
import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockStatus;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockSortField;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummary;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import({StockRepositoryAdapter.class, JpaAuditingConfig.class})
class StockRepositoryAdapterTest {

    @Autowired
    private StockRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void seed() {
        insertWarehouse(2L, "WH-SE-001", "서울 1창고");
        insertWarehouse(5L, "HQ-001", "본사 중앙창고");
    }

    @Test
    void save하면_id를_발급하고_findResultById가_창고코드를_조인하며_createdAt이_채워진다() {
        Long id = adapter.save(Stock.create("HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 100, 50));

        assertThat(id).isNotNull();

        StockCreateResult result = adapter.findResultById(id).orElseThrow();
        assertThat(result.stockId()).isEqualTo(id);
        assertThat(result.sku()).isEqualTo("HMC-EN-00214");
        assertThat(result.warehouseCode()).isEqualTo("WH-SE-001");
        assertThat(result.quantity()).isEqualTo(100);
        assertThat(result.safetyStock()).isEqualTo(50);
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void existsBySkuAndWarehouseId는_저장된_조합에만_true다() {
        adapter.save(Stock.create("HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 100, 50));

        assertThat(adapter.existsBySkuAndWarehouseId("HMC-EN-00214", 2L)).isTrue();
        assertThat(adapter.existsBySkuAndWarehouseId("HMC-EN-00214", 999L)).isFalse();
        assertThat(adapter.existsBySkuAndWarehouseId("OTHER-SKU", 2L)).isFalse();
    }

    // ---- search ----

    @Test
    void 필터없는_search는_전체를_반환하고_페이지메타를_채운다() {
        seedStocks();
        StockSummaryPage page = adapter.search(
                query(null, List.of(), null, StockSortField.NAME, SortDirection.ASC, 1, 20));

        assertThat(page.content()).hasSize(4);
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(1);
    }

    @Test
    void warehouseCodes로_특정_창고_재고만_조회한다() {
        seedStocks();
        StockSummaryPage page = adapter.search(
                query(null, List.of("WH-SE-001"), null, StockSortField.NAME, SortDirection.ASC, 1, 20));

        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content()).extracting(StockSummary::warehouseCode).containsOnly("WH-SE-001");
    }

    @Test
    void status_LOW는_0초과_안전재고_미만만_조회한다() {
        seedStocks();
        StockSummaryPage page = adapter.search(
                query(null, List.of(), StockStatus.LOW, StockSortField.NAME, SortDirection.ASC, 1, 20));

        assertThat(page.content()).extracting(StockSummary::sku).containsExactly("HMC-BR-00788");
    }

    @Test
    void status_OUT은_수량0만_조회한다() {
        seedStocks();
        StockSummaryPage page = adapter.search(
                query(null, List.of(), StockStatus.OUT, StockSortField.NAME, SortDirection.ASC, 1, 20));

        assertThat(page.content()).extracting(StockSummary::sku).containsExactly("HMC-OIL-5W30");
    }

    @Test
    void keyword는_부품명_부분일치로_조회한다() {
        seedStocks();
        StockSummaryPage page = adapter.search(
                query("엔진", List.of(), null, StockSortField.NAME, SortDirection.ASC, 1, 20));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.content()).extracting(StockSummary::sku)
                .containsExactlyInAnyOrder("HMC-EN-00214", "HMC-OIL-5W30");
    }

    @Test
    void sort_quantity_desc는_수량_내림차순이다() {
        seedStocks();
        StockSummaryPage page = adapter.search(
                query(null, List.of(), null, StockSortField.QUANTITY, SortDirection.DESC, 1, 20));

        assertThat(page.content()).extracting(StockSummary::quantity).containsExactly(500, 120, 30, 0);
    }

    @Test
    void sort_safetyRatio_asc는_안전재고_대비_비율_오름차순이다() {
        seedStocks();
        StockSummaryPage page = adapter.search(
                query(null, List.of(), null, StockSortField.SAFETY_RATIO, SortDirection.ASC, 1, 20));

        // 비율: OIL 0/60=0 < BR 30/40=0.75 < EN 120/50=2.4 < TR 500/100=5
        assertThat(page.content()).extracting(StockSummary::sku)
                .containsExactly("HMC-OIL-5W30", "HMC-BR-00788", "HMC-EN-00214", "HMC-TR-00111");
    }

    @Test
    void 페이지네이션은_size로_나누고_totalPages를_계산한다() {
        seedStocks();
        StockSummaryPage page1 = adapter.search(
                query(null, List.of(), null, StockSortField.QUANTITY, SortDirection.DESC, 1, 2));

        assertThat(page1.content()).hasSize(2);
        assertThat(page1.totalElements()).isEqualTo(4);
        assertThat(page1.totalPages()).isEqualTo(2);
        assertThat(page1.content()).extracting(StockSummary::quantity).containsExactly(500, 120);

        StockSummaryPage page2 = adapter.search(
                query(null, List.of(), null, StockSortField.QUANTITY, SortDirection.DESC, 2, 2));
        assertThat(page2.content()).extracting(StockSummary::quantity).containsExactly(30, 0);
    }

    @Test
    void findDetailByWarehouseCodeAndSku는_창고코드와_sku로_단건을_반환한다() {
        seedStocks();

        StockDetail detail = adapter.findDetailByWarehouseCodeAndSku("WH-SE-001", "HMC-EN-00214").orElseThrow();

        assertThat(detail.warehouseCode()).isEqualTo("WH-SE-001");
        assertThat(detail.sku()).isEqualTo("HMC-EN-00214");
        assertThat(detail.quantity()).isEqualTo(120);
        assertThat(detail.safetyStock()).isEqualTo(50);
    }

    @Test
    void findDetailByWarehouseCodeAndSku는_재고행이_없으면_empty다() {
        seedStocks();

        assertThat(adapter.findDetailByWarehouseCodeAndSku("WH-SE-001", "NO-SUCH-SKU")).isEmpty();
    }

    @Test
    void findQuantitiesByWarehouseCodeAndSkus는_해당창고_존재SKU만_반환하고_없는SKU와_타창고는_제외한다() {
        seedStocks();
        insertStock("HMC-EN-00214", "엔진오일 필터", 5L, 500, 100); // HQ-001에도 같은 sku

        List<StockQuantity> rows = adapter.findQuantitiesByWarehouseCodeAndSkus(
                "WH-SE-001", List.of("HMC-EN-00214", "HMC-BR-00788", "NO-SUCH"));

        assertThat(rows).extracting(StockQuantity::sku)
                .containsExactlyInAnyOrder("HMC-EN-00214", "HMC-BR-00788"); // NO-SUCH 생략
        StockQuantity en = rows.stream().filter(r -> r.sku().equals("HMC-EN-00214")).findFirst().orElseThrow();
        assertThat(en.quantity()).isEqualTo(120);   // WH-SE-001 값(HQ-001의 500이 아님)
        assertThat(en.safetyStock()).isEqualTo(50);
    }

    @Test
    void findSkuWarehouseStocks_전체창고는_sku의_모든_창고행을_창고코드순으로_반환한다() {
        seedStocks();
        insertStock("HMC-EN-00214", "엔진오일 필터", 5L, 500, 100); // HQ-001에도 같은 sku

        List<StockSkuRow> rows = adapter.findSkuWarehouseStocks("HMC-EN-00214", List.of());

        assertThat(rows).extracting(StockSkuRow::warehouseCode).containsExactly("HQ-001", "WH-SE-001");
        assertThat(rows).extracting(StockSkuRow::itemName).containsOnly("엔진오일 필터");
    }

    @Test
    void findSkuWarehouseStocks_창고코드_필터는_해당_창고만_반환한다() {
        seedStocks();
        insertStock("HMC-EN-00214", "엔진오일 필터", 5L, 500, 100);

        List<StockSkuRow> rows = adapter.findSkuWarehouseStocks("HMC-EN-00214", List.of("WH-SE-001"));

        assertThat(rows).extracting(StockSkuRow::warehouseCode).containsExactly("WH-SE-001");
        assertThat(rows.get(0).quantity()).isEqualTo(120);
    }

    @Test
    void countByStatus_전체창고는_총_부족_무재고_포지션수를_센다() {
        seedStocks();

        StockStatusCount counts = adapter.countByStatus(List.of());

        assertThat(counts.total()).isEqualTo(4);
        assertThat(counts.low()).isEqualTo(1);   // HMC-BR-00788 (30/40)
        assertThat(counts.out()).isEqualTo(1);   // HMC-OIL-5W30 (0/60)
    }

    @Test
    void countByStatus_창고필터는_해당_창고만_센다() {
        seedStocks();

        StockStatusCount counts = adapter.countByStatus(List.of("WH-SE-001"));

        assertThat(counts.total()).isEqualTo(3);
        assertThat(counts.low()).isEqualTo(1);
        assertThat(counts.out()).isEqualTo(1);
    }

    @Test
    void countByStatus_재고가_없으면_모두_0이다() {
        StockStatusCount counts = adapter.countByStatus(List.of());

        assertThat(counts.total()).isZero();
        assertThat(counts.low()).isZero();
        assertThat(counts.out()).isZero();
    }

    @Test
    void findBySkuAndWarehouseCode는_수정용_재고를_반환한다() {
        seedStocks();

        Stock stock = adapter.findBySkuAndWarehouseCode("HMC-EN-00214", "WH-SE-001").orElseThrow();

        assertThat(stock.getId()).isNotNull();
        assertThat(stock.getWarehouseId()).isEqualTo(2L);
        assertThat(stock.getQuantity()).isEqualTo(120);
        assertThat(stock.getSafetyStock()).isEqualTo(50);
    }

    @Test
    void findBySkuAndWarehouseCode는_없으면_empty다() {
        seedStocks();

        assertThat(adapter.findBySkuAndWarehouseCode("HMC-EN-00214", "NO-WH")).isEmpty();
        assertThat(adapter.findBySkuAndWarehouseCode("NO-SKU", "WH-SE-001")).isEmpty();
    }

    @Test
    void save_기존재고는_현재고를_갱신한다() {
        seedStocks();
        Stock stock = adapter.findBySkuAndWarehouseCode("HMC-EN-00214", "WH-SE-001").orElseThrow();
        int delta = stock.adjust(AdjustmentType.DECREASE, 20); // 120 → 100

        adapter.save(stock);
        testEntityManager.flush();
        testEntityManager.clear();

        Stock reloaded = adapter.findBySkuAndWarehouseCode("HMC-EN-00214", "WH-SE-001").orElseThrow();
        assertThat(delta).isEqualTo(-20);
        assertThat(reloaded.getQuantity()).isEqualTo(100);
    }

    private static StockSearchQuery query(String keyword, List<String> warehouseCodes, StockStatus status,
                                          StockSortField field, SortDirection direction, int page, int size) {
        return new StockSearchQuery(keyword, warehouseCodes, status, field, direction, page, size);
    }

    private void seedStocks() {
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);   // NORMAL
        insertStock("HMC-BR-00788", "브레이크 패드", 2L, 30, 40);    // LOW
        insertStock("HMC-OIL-5W30", "엔진오일 5W30", 2L, 0, 60);     // OUT
        insertStock("HMC-TR-00111", "변속기오일", 5L, 500, 100);     // NORMAL (HQ-001)
    }

    private void insertStock(String sku, String itemName, long warehouseId, int currentStock, int safetyStock) {
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
                .setParameter(5, currentStock)
                .setParameter(6, safetyStock)
                .setParameter(7, Instant.parse("2026-05-20T00:00:00Z"))
                .setParameter(8, Instant.parse("2026-05-20T00:00:00Z"))
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
