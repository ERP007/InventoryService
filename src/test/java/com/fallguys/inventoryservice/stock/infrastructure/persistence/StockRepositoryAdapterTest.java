package com.fallguys.inventoryservice.stock.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.config.JpaAuditingConfig;
import com.fallguys.inventoryservice.shared.exception.OptimisticLockConflictException;
import com.fallguys.inventoryservice.shared.query.SortDirection;
import com.fallguys.inventoryservice.stock.domain.AdjustmentType;
import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockStatus;
import com.fallguys.inventoryservice.stock.domain.command.UpdateSafetyStockCommand;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.query.ItemStockRow;
import com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit;
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
    void status_LOW는_안전재고_미만을_재고0포함_조회한다() {
        seedStocks();
        StockSummaryPage page = adapter.search(
                query(null, List.of(), StockStatus.LOW, StockSortField.NAME, SortDirection.ASC, 1, 20));

        // 부족 = 안전재고 미만(재고 0 포함): BR(30/40), OIL(0/60). 이름 오름차순.
        assertThat(page.content()).extracting(StockSummary::sku)
                .containsExactly("HMC-BR-00788", "HMC-OIL-5W30");
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
    void countByStatus_전체창고는_총과_부족_재고0포함_포지션수를_센다() {
        seedStocks();

        StockStatusCount counts = adapter.countByStatus(List.of());

        assertThat(counts.total()).isEqualTo(4);
        assertThat(counts.low()).isEqualTo(2);   // HMC-BR-00788 (30/40), HMC-OIL-5W30 (0/60, 재고0도 부족)
    }

    @Test
    void countByStatus_창고필터는_해당_창고만_센다() {
        seedStocks();

        StockStatusCount counts = adapter.countByStatus(List.of("WH-SE-001"));

        assertThat(counts.total()).isEqualTo(3);
        assertThat(counts.low()).isEqualTo(2);   // BR(30/40), OIL(0/60) 모두 WH-SE-001
    }

    @Test
    void countByStatus_재고가_없으면_모두_0이다() {
        StockStatusCount counts = adapter.countByStatus(List.of());

        assertThat(counts.total()).isZero();
        assertThat(counts.low()).isZero();
    }

    @Test
    void countByStatus_비활성_창고의_재고는_집계에서_제외한다() {
        seedStocks(); // 활성 창고(2L, 5L)에 4건
        insertWarehouse(9L, "WH-OLD-001", "폐쇄 창고", false);
        insertStock("HMC-EN-00214", "엔진오일 필터", 9L, 10, 50); // 비활성 창고 재고(집계 제외 대상)

        StockStatusCount counts = adapter.countByStatus(List.of());

        assertThat(counts.total()).isEqualTo(4); // 비활성 창고 9L의 1건은 제외된다
    }

    @Test
    void search는_비활성_창고_재고도_조회하되_warehouseActive로_구분한다() {
        insertWarehouse(9L, "WH-OLD-001", "폐쇄 창고", false);
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);  // 활성 창고
        insertStock("HMC-EN-00214", "엔진오일 필터", 9L, 30, 50);   // 비활성 창고

        StockSummaryPage page = adapter.search(
                query(null, List.of(), null, StockSortField.NAME, SortDirection.ASC, 1, 20));

        assertThat(page.totalElements()).isEqualTo(2); // 비활성 창고 재고도 목록에 노출(A3)
        StockSummary active = page.content().stream()
                .filter(s -> s.warehouseCode().equals("WH-SE-001")).findFirst().orElseThrow();
        StockSummary inactive = page.content().stream()
                .filter(s -> s.warehouseCode().equals("WH-OLD-001")).findFirst().orElseThrow();
        assertThat(active.warehouseActive()).isTrue();
        assertThat(inactive.warehouseActive()).isFalse();
    }

    @Test
    void findSkuWarehouseStocks는_비활성_창고_행을_제외한다() {
        insertWarehouse(9L, "WH-OLD-001", "폐쇄 창고", false);
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);  // 활성 창고
        insertStock("HMC-EN-00214", "엔진오일 필터", 9L, 30, 50);   // 비활성 창고

        List<StockSkuRow> rows = adapter.findSkuWarehouseStocks("HMC-EN-00214", List.of());

        // 상세 패널은 활성 창고만 노출한다(비활성 창고 행 제외).
        assertThat(rows).extracting(StockSkuRow::warehouseCode).containsExactly("WH-SE-001");
        assertThat(rows).allMatch(StockSkuRow::itemActive); // itemActive 투영 확인
    }

    // ---- findRecentItemStocks (부품 마스터 창고별 현재고) ----

    @Test
    void findRecentItemStocks_전사는_활성창고_재고를_최근수정순_최대5건_반환한다() {
        insertWarehouse(11L, "WH-A-001", "창고 A");
        insertWarehouse(12L, "WH-B-001", "창고 B");
        insertWarehouse(13L, "WH-C-001", "창고 C");
        insertWarehouse(14L, "WH-D-001", "창고 D");
        insertWarehouse(15L, "WH-E-001", "창고 E");
        insertWarehouse(16L, "WH-F-001", "창고 F");
        // 같은 sku를 6개 창고에 적재하되 updated_at을 다르게 둔다(F가 가장 최근, A가 가장 오래됨).
        insertStockAt("HMC-EN-00214", "엔진오일 필터", 11L, 10, 50, "2026-05-01T00:00:00Z");
        insertStockAt("HMC-EN-00214", "엔진오일 필터", 12L, 10, 50, "2026-05-02T00:00:00Z");
        insertStockAt("HMC-EN-00214", "엔진오일 필터", 13L, 10, 50, "2026-05-03T00:00:00Z");
        insertStockAt("HMC-EN-00214", "엔진오일 필터", 14L, 10, 50, "2026-05-04T00:00:00Z");
        insertStockAt("HMC-EN-00214", "엔진오일 필터", 15L, 10, 50, "2026-05-05T00:00:00Z");
        insertStockAt("HMC-EN-00214", "엔진오일 필터", 16L, 10, 50, "2026-05-06T00:00:00Z");

        List<ItemStockRow> rows = adapter.findRecentItemStocks("HMC-EN-00214", List.of(), 5);

        // 6건 중 최근 수정 5건만, updated_at 내림차순. 가장 오래된 WH-A-001은 잘린다.
        assertThat(rows).extracting(ItemStockRow::warehouseCode)
                .containsExactly("WH-F-001", "WH-E-001", "WH-D-001", "WH-C-001", "WH-B-001");
    }

    @Test
    void findRecentItemStocks_창고필터는_해당_창고만_반환한다() {
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);   // WH-SE-001
        insertStock("HMC-EN-00214", "엔진오일 필터", 5L, 500, 100);  // HQ-001

        List<ItemStockRow> rows = adapter.findRecentItemStocks("HMC-EN-00214", List.of("WH-SE-001"), 5);

        assertThat(rows).extracting(ItemStockRow::warehouseCode).containsExactly("WH-SE-001");
        assertThat(rows.get(0).warehouseName()).isEqualTo("서울 1창고");
        assertThat(rows.get(0).currentStock()).isEqualTo(120);
        assertThat(rows.get(0).safetyStock()).isEqualTo(50);
    }

    @Test
    void findRecentItemStocks_비활성_창고_행은_제외한다() {
        insertWarehouse(9L, "WH-OLD-001", "폐쇄 창고", false);
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);  // 활성 창고
        insertStock("HMC-EN-00214", "엔진오일 필터", 9L, 30, 50);   // 비활성 창고(제외 대상)

        List<ItemStockRow> rows = adapter.findRecentItemStocks("HMC-EN-00214", List.of(), 5);

        assertThat(rows).extracting(ItemStockRow::warehouseCode).containsExactly("WH-SE-001");
    }

    @Test
    void findRecentItemStocks_비활성_부품_재고도_포함한다() {
        insertStock("HMC-CL-00222", "클러치 디스크", 2L, 80, 25, false); // 비활성 아이템(단순 조회라 포함)

        List<ItemStockRow> rows = adapter.findRecentItemStocks("HMC-CL-00222", List.of(), 5);

        assertThat(rows).extracting(ItemStockRow::warehouseCode).containsExactly("WH-SE-001");
        assertThat(rows.get(0).currentStock()).isEqualTo(80);
    }

    @Test
    void findRecentItemStocks_재고가_없으면_빈_리스트다() {
        seedStocks();

        assertThat(adapter.findRecentItemStocks("NO-SUCH", List.of(), 5)).isEmpty();
    }

    // ---- findWarehouseCodesBySku / updateItemNameBySku (아이템 이름 동기화) ----

    @Test
    void findWarehouseCodesBySku는_해당_sku의_창고코드를_활성무관_code오름차순으로_반환한다() {
        insertWarehouse(9L, "WH-OLD-001", "폐쇄 창고", false);       // 비활성 창고
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);   // WH-SE-001 (활성)
        insertStock("HMC-EN-00214", "엔진오일 필터", 5L, 500, 100);  // HQ-001 (활성)
        insertStock("HMC-EN-00214", "엔진오일 필터", 9L, 10, 50);    // WH-OLD-001 (비활성)
        insertStock("HMC-BR-00788", "브레이크 패드", 2L, 30, 40);    // 다른 sku

        List<String> codes = adapter.findWarehouseCodesBySku("HMC-EN-00214");

        // 비활성 창고(WH-OLD-001)도 포함(창고 활성 무관), code 오름차순, 다른 sku 제외
        assertThat(codes).containsExactly("HQ-001", "WH-OLD-001", "WH-SE-001");
    }

    @Test
    void findWarehouseCodesBySku는_재고가_없으면_빈_리스트다() {
        seedStocks();

        assertThat(adapter.findWarehouseCodesBySku("NO-SUCH")).isEmpty();
    }

    @Test
    void updateItemNameBySku는_해당_sku_모든_행_이름을_바꾸고_변경행수를_반환한다() {
        insertWarehouse(9L, "WH-OLD-001", "폐쇄 창고", false);
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);   // 활성
        insertStock("HMC-EN-00214", "엔진오일 필터", 9L, 10, 50);    // 비활성 창고도 갱신 대상
        insertStock("HMC-BR-00788", "브레이크 패드", 2L, 30, 40);    // 다른 sku(안 바뀜)

        int updated = adapter.updateItemNameBySku("HMC-EN-00214", "엔진오일 필터(개선형)");
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(updated).isEqualTo(2); // 활성+비활성 모두(창고 활성 무관)
        // 활성 창고 행은 이름이 바뀐 게 보인다(findSkuWarehouseStocks는 활성 창고만 노출)
        List<StockSkuRow> en = adapter.findSkuWarehouseStocks("HMC-EN-00214", List.of());
        assertThat(en).extracting(StockSkuRow::itemName).containsExactly("엔진오일 필터(개선형)");
        // 다른 sku는 그대로
        List<StockSkuRow> br = adapter.findSkuWarehouseStocks("HMC-BR-00788", List.of());
        assertThat(br.get(0).itemName()).isEqualTo("브레이크 패드");
    }

    @Test
    void updateItemNameBySku는_대상_행이_없으면_0을_반환한다() {
        seedStocks();

        assertThat(adapter.updateItemNameBySku("NO-SUCH", "이름")).isZero();
    }

    @Test
    void updateItemUnitBySku는_해당_sku_모든_행_단위를_바꾸고_변경행수를_반환한다() {
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);  // EA
        insertStock("HMC-EN-00214", "엔진오일 필터", 5L, 500, 100); // EA
        insertStock("HMC-BR-00788", "브레이크 패드", 2L, 30, 40);   // EA(다른 sku, 안 바뀜)

        int updated = adapter.updateItemUnitBySku("HMC-EN-00214", ItemUnit.BOX);
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(updated).isEqualTo(2);
        List<StockSkuRow> en = adapter.findSkuWarehouseStocks("HMC-EN-00214", List.of());
        assertThat(en).extracting(StockSkuRow::itemUnit).containsOnly(ItemUnit.BOX);
        List<StockSkuRow> br = adapter.findSkuWarehouseStocks("HMC-BR-00788", List.of());
        assertThat(br.get(0).itemUnit()).isEqualTo(ItemUnit.EA);
    }

    @Test
    void updateItemActiveBySku는_해당_sku_모든_행_활성여부를_바꾸고_변경행수를_반환한다() {
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);  // item_active 기본 true
        insertStock("HMC-EN-00214", "엔진오일 필터", 5L, 500, 100); // true
        insertStock("HMC-BR-00788", "브레이크 패드", 2L, 30, 40);   // 다른 sku(안 바뀜)

        int updated = adapter.updateItemActiveBySku("HMC-EN-00214", false);
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(updated).isEqualTo(2);
        // 비활성 아이템도 활성 창고 행이면 조회된다(findSkuWarehouseStocks는 창고 활성만 거름) → itemActive 투영 확인
        List<StockSkuRow> en = adapter.findSkuWarehouseStocks("HMC-EN-00214", List.of());
        assertThat(en).extracting(StockSkuRow::itemActive).containsOnly(false);
        List<StockSkuRow> br = adapter.findSkuWarehouseStocks("HMC-BR-00788", List.of());
        assertThat(br.get(0).itemActive()).isTrue();
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
    void findBySkuAndWarehouseIdForUpdate는_비관락으로_재고를_반환한다() {
        seedStocks();

        Stock stock = adapter.findBySkuAndWarehouseIdForUpdate("HMC-EN-00214", 2L).orElseThrow();

        assertThat(stock.getId()).isNotNull();
        assertThat(stock.getWarehouseId()).isEqualTo(2L);
        assertThat(stock.getQuantity()).isEqualTo(120);
        assertThat(stock.getSafetyStock()).isEqualTo(50);
    }

    @Test
    void findBySkuAndWarehouseIdForUpdate는_없으면_empty다() {
        seedStocks();

        assertThat(adapter.findBySkuAndWarehouseIdForUpdate("HMC-EN-00214", 999L)).isEmpty();
        assertThat(adapter.findBySkuAndWarehouseIdForUpdate("NO-SKU", 2L)).isEmpty();
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

    @Test
    void findSafetyStockEdit_현재안전재고와_version을_반환한다() {
        seedStocks();

        SafetyStockEdit edit = adapter.findSafetyStockEdit("WH-SE-001", "HMC-EN-00214").orElseThrow();

        assertThat(edit.sku()).isEqualTo("HMC-EN-00214");
        assertThat(edit.warehouseCode()).isEqualTo("WH-SE-001");
        assertThat(edit.quantity()).isEqualTo(120);
        assertThat(edit.safetyStock()).isEqualTo(50);
        assertThat(edit.version()).isNotNull();
    }

    @Test
    void findSafetyStockEdit_재고행이_없으면_empty다() {
        seedStocks();

        assertThat(adapter.findSafetyStockEdit("WH-SE-001", "NO-SUCH")).isEmpty();
    }

    @Test
    void updateSafetyStock_version이_일치하면_안전재고를_교체하고_version을_올린다() {
        seedStocks();
        SafetyStockEdit before = adapter.findSafetyStockEdit("WH-SE-001", "HMC-EN-00214").orElseThrow();

        SafetyStockEdit after = adapter.updateSafetyStock(
                new UpdateSafetyStockCommand("WH-SE-001", "HMC-EN-00214", 80, before.version()));
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(after.safetyStock()).isEqualTo(80);
        SafetyStockEdit reloaded = adapter.findSafetyStockEdit("WH-SE-001", "HMC-EN-00214").orElseThrow();
        assertThat(reloaded.safetyStock()).isEqualTo(80);
        assertThat(reloaded.quantity()).isEqualTo(120); // 현재고는 건드리지 않는다
    }

    @Test
    void updateSafetyStock_version이_불일치하면_OptimisticLockConflictException() {
        seedStocks();

        assertThatThrownBy(() -> adapter.updateSafetyStock(
                new UpdateSafetyStockCommand("WH-SE-001", "HMC-EN-00214", 80, 999L)))
                .isInstanceOf(OptimisticLockConflictException.class);
    }

    @Test
    void updateSafetyStock_재고행이_없으면_StockNotFoundException() {
        seedStocks();

        assertThatThrownBy(() -> adapter.updateSafetyStock(
                new UpdateSafetyStockCommand("WH-SE-001", "NO-SUCH", 80, 0L)))
                .isInstanceOf(StockNotFoundException.class);
    }

    @Test
    void countByStatus_비활성_아이템의_재고는_집계에서_제외한다() {
        seedStocks(); // 활성 4건
        insertStock("HMC-CL-00222", "클러치 디스크", 2L, 10, 50, false); // 비활성 아이템

        StockStatusCount counts = adapter.countByStatus(List.of());

        assertThat(counts.total()).isEqualTo(4); // 비활성 아이템 1건은 제외된다
    }

    @Test
    void search는_비활성_아이템_재고도_조회하되_itemActive로_구분한다() {
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);        // 활성 아이템
        insertStock("HMC-CL-00222", "클러치 디스크", 2L, 80, 25, false);  // 비활성 아이템

        StockSummaryPage page = adapter.search(
                query(null, List.of(), null, StockSortField.NAME, SortDirection.ASC, 1, 20));

        assertThat(page.totalElements()).isEqualTo(2); // 비활성 아이템도 목록엔 노출(B3)
        StockSummary active = page.content().stream()
                .filter(s -> s.sku().equals("HMC-EN-00214")).findFirst().orElseThrow();
        StockSummary inactive = page.content().stream()
                .filter(s -> s.sku().equals("HMC-CL-00222")).findFirst().orElseThrow();
        assertThat(active.itemActive()).isTrue();
        assertThat(inactive.itemActive()).isFalse();
    }

    private static StockSearchQuery query(String keyword, List<String> warehouseCodes, StockStatus status,
                                          StockSortField field, SortDirection direction, int page, int size) {
        return new StockSearchQuery(keyword, warehouseCodes, status, field, direction, page, size);
    }

    private void seedStocks() {
        insertStock("HMC-EN-00214", "엔진오일 필터", 2L, 120, 50);   // NORMAL
        insertStock("HMC-BR-00788", "브레이크 패드", 2L, 30, 40);    // LOW
        insertStock("HMC-OIL-5W30", "엔진오일 5W30", 2L, 0, 60);     // LOW(재고0, 안전재고 미만)
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

    private void insertStockAt(String sku, String itemName, long warehouseId, int currentStock, int safetyStock,
                               String updatedAt) {
        // 최근 수정 순 정렬 검증용: updated_at(=created_at)을 명시적으로 둔다(네이티브 insert라 Auditing 미동작).
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
                .setParameter(7, Instant.parse(updatedAt))
                .setParameter(8, Instant.parse(updatedAt))
                .setParameter(9, 0L)
                .executeUpdate();
    }

    private void insertStock(String sku, String itemName, long warehouseId, int currentStock, int safetyStock,
                             boolean itemActive) {
        String itemUnit = itemName.contains("오일") && !itemName.contains("필터") ? "L" : "EA";
        entityManager().createNativeQuery("""
                        INSERT INTO stock
                            (sku, item_name, item_unit, warehouse_id, current_stock, safety_stock, item_active,
                             created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, sku)
                .setParameter(2, itemName)
                .setParameter(3, itemUnit)
                .setParameter(4, warehouseId)
                .setParameter(5, currentStock)
                .setParameter(6, safetyStock)
                .setParameter(7, itemActive)
                .setParameter(8, Instant.parse("2026-05-20T00:00:00Z"))
                .setParameter(9, Instant.parse("2026-05-20T00:00:00Z"))
                .setParameter(10, 0L)
                .executeUpdate();
    }

    private void insertWarehouse(long id, String code, String name) {
        insertWarehouse(id, code, name, true);
    }

    private void insertWarehouse(long id, String code, String name, boolean active) {
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
                .setParameter(7, active)
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
