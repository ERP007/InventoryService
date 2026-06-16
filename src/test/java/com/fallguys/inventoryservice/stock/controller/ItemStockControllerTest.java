package com.fallguys.inventoryservice.stock.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fallguys.inventoryservice.shared.model.UserRole;
import com.fallguys.inventoryservice.shared.security.SecurityConfig;
import com.fallguys.inventoryservice.shared.security.TestJwtDecoderConfig;
import com.fallguys.inventoryservice.shared.web.GlobalExceptionHandler;
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockRepository;
import com.fallguys.inventoryservice.stock.domain.StockService;
import com.fallguys.inventoryservice.stock.domain.command.UpdateSafetyStockCommand;
import com.fallguys.inventoryservice.stock.domain.query.ItemStockRow;
import com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit;
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
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

@WebMvcTest(ItemStockController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TestJwtDecoderConfig.class, ItemStockControllerTest.StubConfig.class})
class ItemStockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // tenancy_code=WH-SE-001(BRANCH 자기 창고). ADMIN/HQ는 tenancy_code를 안 쓰지만 동일 헬퍼로 발급한다.
    private static RequestPostProcessor roleJwt(UserRole role) {
        return jwt().jwt(token -> token
                .claim("employee_no", "tester")
                .claim("name", "홍길동")
                .claim("user_role", role.name())
                .claim("tenancy_type", tenancyTypeOf(role))
                .claim("tenancy_code", "WH-SE-001"));
    }

    private static String tenancyTypeOf(UserRole role) {
        if (role.name().startsWith("HQ")) {
            return "HQ";
        }
        if (role.name().startsWith("BRANCH")) {
            return "BRANCH";
        }
        return "ADMIN";
    }

    // ---- ADMIN·HQ : warehouseCode 없으면 전사, 있으면 선택 창고 ----

    @Test
    void ADMIN이_warehouseCode없이_조회하면_200과_전체창고_재고_파생status를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/items/HMC-EN-00214/stocks").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.stocks.length()").value(2))
                .andExpect(jsonPath("$.stocks[0].warehouseCode").value("WH-HQ-001"))
                .andExpect(jsonPath("$.stocks[0].warehouseName").value("본사 중앙창고"))
                .andExpect(jsonPath("$.stocks[0].currentStock").value(185))
                .andExpect(jsonPath("$.stocks[0].safetyStock").value(120))
                .andExpect(jsonPath("$.stocks[0].status").value("NORMAL"))
                .andExpect(jsonPath("$.stocks[1].warehouseCode").value("WH-GN-001"))
                .andExpect(jsonPath("$.stocks[1].currentStock").value(2))
                .andExpect(jsonPath("$.stocks[1].status").value("LOW"));
    }

    @Test
    void ADMIN이_warehouseCode를_선택하면_200과_해당창고_재고만_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/items/HMC-EN-00214/stocks")
                        .param("warehouseCode", "WH-GN-001")
                        .with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.stocks.length()").value(1))
                .andExpect(jsonPath("$.stocks[0].warehouseCode").value("WH-GN-001"))
                .andExpect(jsonPath("$.stocks[0].currentStock").value(0))
                .andExpect(jsonPath("$.stocks[0].status").value("OUT"));
    }

    @Test
    void HQ_STAFF도_전_Role이라_200으로_조회된다() throws Exception {
        mockMvc.perform(get("/inventory/items/HMC-EN-00214/stocks").with(roleJwt(UserRole.HQ_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks.length()").value(2));
    }

    @Test
    void ADMIN이_없는_warehouseCode를_지정하면_404와_WAREHOUSE_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/items/HMC-EN-00214/stocks")
                        .param("warehouseCode", "NOPE-999")
                        .with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    // ---- BRANCH : 자기 창고만, 타 창고는 403 ----

    @Test
    void BRANCH가_warehouseCode없이_조회하면_200과_자기창고_재고만_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/items/HMC-EN-00214/stocks").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks.length()").value(1))
                .andExpect(jsonPath("$.stocks[0].warehouseCode").value("WH-SE-001"))
                .andExpect(jsonPath("$.stocks[0].currentStock").value(48))
                .andExpect(jsonPath("$.stocks[0].status").value("LOW"));
    }

    @Test
    void BRANCH가_본인창고를_지정하면_200으로_조회된다() throws Exception {
        mockMvc.perform(get("/inventory/items/HMC-EN-00214/stocks")
                        .param("warehouseCode", "WH-SE-001")
                        .with(roleJwt(UserRole.BRANCH_MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks[0].warehouseCode").value("WH-SE-001"));
    }

    @Test
    void BRANCH가_타_창고를_지정하면_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/items/HMC-EN-00214/stocks")
                        .param("warehouseCode", "WH-GN-001")
                        .with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    // ---- 재고 없음 / 형식 오류 / 인증 ----

    @Test
    void 재고행이_없어도_404가_아니라_200과_빈_stocks를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/items/NO-STOCK-SKU/stocks").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("NO-STOCK-SKU"))
                .andExpect(jsonPath("$.stocks.length()").value(0));
    }

    @Test
    void sku에_하이픈이_없으면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/items/NOHYPHEN/stocks").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("sku"));
    }

    @Test
    void warehouseCode가_빈값이면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/items/HMC-EN-00214/stocks")
                        .param("warehouseCode", "")
                        .with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("warehouseCode"));
    }

    @Test
    void 인증토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/items/HMC-EN-00214/stocks"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        StockService stockService() {
            StockRepository stockRepository = new StockRepository() {
                @Override
                public List<ItemStockRow> findRecentItemStocks(String sku, List<String> warehouseCodes, int limit) {
                    if (!"HMC-EN-00214".equals(sku)) {
                        return List.of(); // 재고 없는 SKU(NO-STOCK-SKU 등)
                    }
                    if (warehouseCodes.isEmpty()) { // ADMIN·HQ 전사
                        return List.of(
                                new ItemStockRow("WH-HQ-001", "본사 중앙창고", 185, 120),  // NORMAL
                                new ItemStockRow("WH-GN-001", "강남 1지점 창고", 2, 120));   // LOW
                    }
                    if (warehouseCodes.equals(List.of("WH-GN-001"))) { // ADMIN·HQ 선택 창고
                        return List.of(new ItemStockRow("WH-GN-001", "강남 1지점 창고", 0, 120)); // OUT
                    }
                    if (warehouseCodes.equals(List.of("WH-SE-001"))) { // BRANCH 자기 창고
                        return List.of(new ItemStockRow("WH-SE-001", "서울 1창고", 48, 50)); // LOW
                    }
                    return List.of();
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
            };

            WarehouseRepository warehouseRepository = new WarehouseRepository() {
                @Override
                public boolean existsByCode(String code) {
                    return List.of("WH-HQ-001", "WH-GN-001", "WH-SE-001").contains(code);
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
                public Long save(Warehouse warehouse) {
                    return null;
                }

                @Override
                public Optional<WarehouseSummary> findSummaryById(Long id) {
                    return Optional.empty();
                }

                @Override
                public Optional<WarehouseSummaryForEdit> findForEditByCode(String code) {
                    return Optional.empty();
                }

                @Override
                public WarehouseSummaryForEdit update(String code, UpdateWarehouseCommand command) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public WarehouseSummaryForEdit changeActive(String code, ChangeWarehouseActiveCommand command) {
                    throw new UnsupportedOperationException();
                }
            };

            // 이 API는 Item 마스터를 호출하지 않는다(단순 조회) — no-op provider로 충분.
            return new StockService(stockRepository, warehouseRepository, sku -> Optional.empty());
        }
    }
}
