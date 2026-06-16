package com.fallguys.inventoryservice.stock.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fallguys.inventoryservice.stock.domain.command.UpdateSafetyStockCommand;
import com.fallguys.inventoryservice.stock.domain.query.ItemSyncResult;
import com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

class StockItemSyncServiceTest {

    @Test
    void syncItemName은_sku의_모든_창고행_이름을_갱신하고_변경창고와_건수를_반환한다() {
        StubStockRepository repo = new StubStockRepository();
        repo.codes = List.of("HQ-001", "WH-SE-001");
        repo.updateReturn = 2;
        StockItemSyncService service = new StockItemSyncService(repo);

        ItemSyncResult result = service.syncItemName("HMC-EN-00214", "엔진오일 필터(개선형)");

        assertThat(result.sku()).isEqualTo("HMC-EN-00214");
        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(result.warehouseCodes()).containsExactly("HQ-001", "WH-SE-001");
        // 리포지토리에 sku·이름이 그대로 전달됐는지 확인
        assertThat(repo.updatedSku).isEqualTo("HMC-EN-00214");
        assertThat(repo.updatedName).isEqualTo("엔진오일 필터(개선형)");
    }

    @Test
    void syncItemName은_대상_행이_없으면_0건과_빈_창고목록을_반환한다() {
        StubStockRepository repo = new StubStockRepository(); // codes=[], updateReturn=0
        StockItemSyncService service = new StockItemSyncService(repo);

        ItemSyncResult result = service.syncItemName("NO-SUCH", "이름");

        assertThat(result.updatedCount()).isZero();
        assertThat(result.warehouseCodes()).isEmpty();
    }

    @Test
    void syncItemUnit은_sku의_모든_창고행_단위를_갱신하고_변경창고와_건수를_반환한다() {
        StubStockRepository repo = new StubStockRepository();
        repo.codes = List.of("HQ-001", "WH-SE-001");
        repo.updateReturn = 2;
        StockItemSyncService service = new StockItemSyncService(repo);

        ItemSyncResult result = service.syncItemUnit("HMC-EN-00214", ItemUnit.BOX);

        assertThat(result.sku()).isEqualTo("HMC-EN-00214");
        assertThat(result.updatedCount()).isEqualTo(2);
        assertThat(result.warehouseCodes()).containsExactly("HQ-001", "WH-SE-001");
        assertThat(repo.updatedSku).isEqualTo("HMC-EN-00214");
        assertThat(repo.updatedUnit).isEqualTo(ItemUnit.BOX);
    }

    private static final class StubStockRepository implements StockRepository {
        private List<String> codes = List.of();
        private int updateReturn = 0;
        private String updatedSku;
        private String updatedName;
        private ItemUnit updatedUnit;

        @Override
        public List<String> findWarehouseCodesBySku(String sku) {
            return codes;
        }

        @Override
        public int updateItemNameBySku(String sku, String itemName) {
            this.updatedSku = sku;
            this.updatedName = itemName;
            return updateReturn;
        }

        @Override
        public int updateItemUnitBySku(String sku, ItemUnit itemUnit) {
            this.updatedSku = sku;
            this.updatedUnit = itemUnit;
            return updateReturn;
        }

        // --- 이 서비스가 쓰지 않는 추상 메서드(trivial) ---
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
        public Long save(Stock stock) {
            return null;
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

        @Override
        public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
            return Optional.empty();
        }

        @Override
        public Optional<Stock> findBySkuAndWarehouseIdForUpdate(String sku, Long warehouseId) {
            return Optional.empty();
        }

        @Override
        public Optional<SafetyStockEdit> findSafetyStockEdit(String warehouseCode, String sku) {
            return Optional.empty();
        }

        @Override
        public SafetyStockEdit updateSafetyStock(UpdateSafetyStockCommand command) {
            return null;
        }
    }
}
