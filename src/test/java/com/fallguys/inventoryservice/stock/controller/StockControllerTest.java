package com.fallguys.inventoryservice.stock.controller;

import com.fallguys.inventoryservice.shared.web.GlobalExceptionHandler;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockRepository;
import com.fallguys.inventoryservice.stock.domain.StockService;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.warehouse.domain.Warehouse;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseRepository;
import com.fallguys.inventoryservice.warehouse.domain.command.ChangeWarehouseActiveCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

@WebMvcTest(StockController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, StockControllerTest.StubConfig.class})
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor roleJwt(UserRole role) {
        return jwt().jwt(token -> token
                .claim("employee_no", "tester")
                .claim("user_role", role.name()));
    }

    @Test
    void 정상생성은_201과_생성된_재고와_파생status를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","itemName":"엔진오일 필터","warehouseCode":"WH-SE-001","quantity":100,"safetyStock":50}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockId").value(1050))
                .andExpect(jsonPath("$.sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.warehouseCode").value("WH-SE-001"))
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.safetyStock").value(50))
                .andExpect(jsonPath("$.status").value("NORMAL"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void 생성은_권한없는_HQ_MANAGER면_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks")
                        .with(roleJwt(UserRole.HQ_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","itemName":"엔진오일 필터","warehouseCode":"WH-SE-001","quantity":100,"safetyStock":50}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void 인증토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","itemName":"엔진오일 필터","warehouseCode":"WH-SE-001","quantity":100,"safetyStock":50}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 수량이_음수면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","itemName":"엔진오일 필터","warehouseCode":"WH-SE-001","quantity":-1,"safetyStock":50}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void 필수값_누락은_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemName":"엔진오일 필터","warehouseCode":"WH-SE-001","quantity":100,"safetyStock":50}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("sku"));
    }

    @Test
    void 없는_창고코드면_404와_WAREHOUSE_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","itemName":"엔진오일 필터","warehouseCode":"NOPE","quantity":100,"safetyStock":50}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void 이미_존재하는_재고면_409와_STOCK_ALREADY_EXISTS를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"DUP-SKU","itemName":"엔진오일 필터","warehouseCode":"WH-SE-001","quantity":100,"safetyStock":50}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_ALREADY_EXISTS"));
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        StockService stockService() {
            StockRepository stockRepository = new StockRepository() {
                private Stock saved;

                @Override
                public boolean existsBySkuAndWarehouseId(String sku, Long warehouseId) {
                    return "DUP-SKU".equals(sku);
                }

                @Override
                public Long save(Stock stock) {
                    this.saved = stock;
                    return 1050L;
                }

                @Override
                public Optional<StockCreateResult> findResultById(Long id) {
                    return Optional.of(new StockCreateResult(
                            1050L, saved.getSku(), "WH-SE-001", saved.getQuantity(), saved.getSafetyStock(),
                            Instant.parse("2026-05-28T14:36:00Z")));
                }
            };

            WarehouseRepository warehouseRepository = new WarehouseRepository() {
                @Override
                public Optional<WarehouseSummaryForEdit> findForEditByCode(String code) {
                    if (!"WH-SE-001".equals(code)) {
                        return Optional.empty();
                    }
                    return Optional.of(new WarehouseSummaryForEdit(
                            2L, code, "서울 1창고", WarehouseType.DEALER, 3L, "지점", "주소", true,
                            Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"), 0L));
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
                public boolean existsByCode(String code) {
                    return false;
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
                public WarehouseSummaryForEdit update(String code, UpdateWarehouseCommand command) {
                    return null;
                }

                @Override
                public WarehouseSummaryForEdit changeActive(String code, ChangeWarehouseActiveCommand command) {
                    return null;
                }
            };

            return new StockService(stockRepository, warehouseRepository);
        }
    }
}
