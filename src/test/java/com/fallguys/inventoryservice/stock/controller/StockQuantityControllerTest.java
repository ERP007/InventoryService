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

import com.fallguys.inventoryservice.shared.security.SecurityConfig;
import com.fallguys.inventoryservice.shared.web.GlobalExceptionHandler;
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockRepository;
import com.fallguys.inventoryservice.stock.domain.StockService;
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

@WebMvcTest(StockQuantityController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, StockQuantityControllerTest.StubConfig.class})
class StockQuantityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor branchJwt(String tenancyCode) {
        return jwt().jwt(token -> token
                .claim("employee_no", "svc")
                .claim("user_role", "BRANCH_STAFF")
                .claim("tenancy_type", "BRANCH")
                .claim("tenancy_code", tenancyCode));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                .claim("employee_no", "svc")
                .claim("user_role", "ADMIN")
                .claim("tenancy_type", "ADMIN"));
    }

    @Test
    void BRANCH는_요청창고를_무시하고_자기창고로_조회한다() throws Exception {
        // 요청은 HQ-001이지만 BRANCH는 자기 창고(WH-SE-001)로 강제된다.
        mockMvc.perform(get("/inventory/stocks/quantities")
                        .param("warehouseCode", "HQ-001")
                        .param("skus", "HMC-EN-00214,HMC-BR-00788")
                        .with(branchJwt("WH-SE-001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseCode").value("WH-SE-001")) // 강제됨
                .andExpect(jsonPath("$.stocks.length()").value(2));
    }

    @Test
    void ADMIN은_요청창고를_그대로_조회하고_없는_SKU는_생략한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/quantities")
                        .param("warehouseCode", "WH-SE-001")
                        .param("skus", "HMC-EN-00214,HMC-BR-00788,NO-SUCH")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseCode").value("WH-SE-001"))
                .andExpect(jsonPath("$.stocks.length()").value(2)) // NO-SUCH 생략
                .andExpect(jsonPath("$.stocks[0].sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.stocks[0].quantity").value(120))
                .andExpect(jsonPath("$.stocks[0].safetyStock").value(50));
    }

    @Test
    void 반복파라미터_skus_A_B형태도_콤마처럼_처리된다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/quantities")
                        .param("warehouseCode", "WH-SE-001")
                        .param("skus", "HMC-EN-00214", "HMC-BR-00788")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks.length()").value(2));
    }

    @Test
    void ADMIN이_warehouseCode를_안주면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/quantities")
                        .param("skus", "HMC-EN-00214")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("warehouseCode"));
    }

    @Test
    void skus_누락은_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/quantities")
                        .param("warehouseCode", "WH-SE-001")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("skus"));
    }

    @Test
    void 없는_창고는_404와_WAREHOUSE_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/quantities")
                        .param("warehouseCode", "NOPE-999")
                        .param("skus", "HMC-EN-00214")
                        .with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void 인증토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/quantities")
                        .param("warehouseCode", "WH-SE-001")
                        .param("skus", "HMC-EN-00214"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        StockService stockService() {
            StockRepository stockRepository = new StockRepository() {
                @Override
                public List<StockQuantity> findQuantitiesByWarehouseCodeAndSkus(String warehouseCode, List<String> skus) {
                    // 요청 SKU 중 재고가 있는 것만 반환(NO-SUCH류는 생략).
                    return List.of(
                                    new StockQuantity("HMC-EN-00214", 120, 50),
                                    new StockQuantity("HMC-BR-00788", 30, 40)).stream()
                            .filter(q -> skus.contains(q.sku()))
                            .toList();
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
                public Optional<com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit> findSafetyStockEdit(
                        String warehouseCode, String sku) {
                    return Optional.empty();
                }

                @Override
                public com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit updateSafetyStock(
                        com.fallguys.inventoryservice.stock.domain.command.UpdateSafetyStockCommand command) {
                    return null;
                }
            };

            WarehouseRepository warehouseRepository = new WarehouseRepository() {
                @Override
                public boolean existsByCode(String code) {
                    return "WH-SE-001".equals(code);
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

            // quantities는 getDetail을 거치지 않아 Item 호출이 없다 — no-op provider로 충분.
            return new StockService(stockRepository, warehouseRepository, sku -> Optional.empty());
        }
    }
}
