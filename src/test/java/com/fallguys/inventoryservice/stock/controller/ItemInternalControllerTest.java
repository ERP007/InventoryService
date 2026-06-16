package com.fallguys.inventoryservice.stock.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fallguys.inventoryservice.shared.model.UserRole;
import com.fallguys.inventoryservice.shared.security.SecurityConfig;
import com.fallguys.inventoryservice.shared.security.TestJwtDecoderConfig;
import com.fallguys.inventoryservice.shared.web.GlobalExceptionHandler;
import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockItemSyncService;
import com.fallguys.inventoryservice.stock.domain.StockRepository;
import com.fallguys.inventoryservice.stock.domain.command.UpdateSafetyStockCommand;
import com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;

@WebMvcTest(ItemInternalController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TestJwtDecoderConfig.class, ItemInternalControllerTest.StubConfig.class})
class ItemInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor roleJwt(UserRole role) {
        return jwt().jwt(token -> token
                .claim("employee_no", "svc")
                .claim("name", "동기화봇")
                .claim("user_role", role.name())
                .claim("tenancy_type", role.name().startsWith("BRANCH") ? "BRANCH" : role.name().startsWith("HQ") ? "HQ" : "ADMIN")
                .claim("tenancy_code", "WH-SE-001"));
    }

    @Test
    void 이름동기화는_ADMIN이면_200과_변경건수_창고코드를_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/inventory/items/HMC-EN-00214/name")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"엔진오일 필터(개선형)"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.updatedCount").value(2))
                .andExpect(jsonPath("$.warehouseCodes.length()").value(2))
                .andExpect(jsonPath("$.warehouseCodes[0]").value("BR-SE-001"))
                .andExpect(jsonPath("$.warehouseCodes[1]").value("HQ-001"));
    }

    @Test
    void 이름동기화는_HQ_STAFF도_허용된다() throws Exception {
        mockMvc.perform(patch("/internal/inventory/items/HMC-EN-00214/name")
                        .with(roleJwt(UserRole.HQ_STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"엔진오일 필터(개선형)"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void 이름동기화는_BRANCH_MANAGER면_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/inventory/items/HMC-EN-00214/name")
                        .with(roleJwt(UserRole.BRANCH_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"엔진오일 필터(개선형)"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void 이름동기화는_itemName이_공백이면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/inventory/items/HMC-EN-00214/name")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("itemName"));
    }

    @Test
    void 인증토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/inventory/items/HMC-EN-00214/name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"엔진오일 필터(개선형)"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    // ---- PATCH /{sku}/unit (단위 동기화) ----

    @Test
    void 단위동기화는_ADMIN이면_200과_변경건수_창고코드를_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/inventory/items/HMC-EN-00214/unit")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemUnit":"BOX"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.updatedCount").value(2))
                .andExpect(jsonPath("$.warehouseCodes.length()").value(2));
    }

    @Test
    void 단위동기화는_BRANCH_STAFF면_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/inventory/items/HMC-EN-00214/unit")
                        .with(roleJwt(UserRole.BRANCH_STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemUnit":"BOX"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void 단위동기화는_itemUnit_누락이면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/inventory/items/HMC-EN-00214/unit")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("itemUnit"));
    }

    @Test
    void 단위동기화는_itemUnit이_허용밖_값이면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(patch("/internal/inventory/items/HMC-EN-00214/unit")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemUnit":"PALLET"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        StockItemSyncService stockItemSyncService() {
            StockRepository stockRepository = new StockRepository() {
                @Override
                public List<String> findWarehouseCodesBySku(String sku) {
                    return "HMC-EN-00214".equals(sku) ? List.of("BR-SE-001", "HQ-001") : List.of();
                }

                @Override
                public int updateItemNameBySku(String sku, String itemName) {
                    return "HMC-EN-00214".equals(sku) ? 2 : 0;
                }

                @Override
                public int updateItemUnitBySku(String sku, ItemUnit itemUnit) {
                    return "HMC-EN-00214".equals(sku) ? 2 : 0;
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
            return new StockItemSyncService(stockRepository);
        }
    }
}
