package com.fallguys.inventoryservice.stock.controller;

import com.fallguys.inventoryservice.shared.web.GlobalExceptionHandler;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSummary;
import com.fallguys.inventoryservice.stock.domain.query.StockSummaryPage;
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

    // ---- GET (목록 조회) : 전체 Role, Tenancy 차등 ----

    @Test
    void 목록조회는_200과_content_파생status_페이지메타를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1001))
                .andExpect(jsonPath("$.content[0].sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("WH-SE-001"))
                .andExpect(jsonPath("$.content[0].warehouseName").value("서울 1창고"))
                .andExpect(jsonPath("$.content[0].quantity").value(48))
                .andExpect(jsonPath("$.content[0].safetyStock").value(50))
                .andExpect(jsonPath("$.content[0].status").value("LOW"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(42))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void 목록조회는_가장_낮은_BRANCH_STAFF도_200으로_조회된다() throws Exception {
        mockMvc.perform(get("/inventory/stocks").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isOk());
    }

    @Test
    void 허용되지_않는_sort는_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks").param("sort", "price,asc").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("sort"));
    }

    @Test
    void 허용되지_않는_size는_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks").param("size", "33").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("size"));
    }

    // ---- GET /{warehouseCode}/{sku} (단건) : BRANCH_* 전용, 자기 창고만 ----

    @Test
    void 단건조회는_200과_현재고_안전재고를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/WH-SE-001/EO-5W30-1L").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseCode").value("WH-SE-001"))
                .andExpect(jsonPath("$.sku").value("EO-5W30-1L"))
                .andExpect(jsonPath("$.quantity").value(48))
                .andExpect(jsonPath("$.safetyStock").value(50));
    }

    @Test
    void 재고행이_없으면_200과_quantity0_safetyStock0을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/WH-SE-001/UNREGISTERED").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.safetyStock").value(0));
    }

    @Test
    void 단건조회는_BRANCH가_아닌_ADMIN이면_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/WH-SE-001/EO-5W30-1L").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void 자기_담당이_아닌_창고면_404와_STOCK_NOT_FOUND를_반환한다() throws Exception {
        // roleJwt의 tenancy_code=WH-SE-001 인데 다른 창고 코드로 호출 → 존재 은닉 404
        mockMvc.perform(get("/inventory/stocks/WH-OTHER-999/EO-5W30-1L").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_NOT_FOUND"));
    }

    // ---- POST (생성) : ADMIN 전용 ----

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
                public StockSummaryPage search(StockSearchQuery query) {
                    StockSummary item = new StockSummary(
                            1001L, "HMC-EN-00214", "엔진오일 필터", 2L, "WH-SE-001", "서울 1창고",
                            48, 50, Instant.parse("2026-05-20T14:22:00Z"));
                    return new StockSummaryPage(List.of(item), query.page(), query.size(), 42, 3);
                }

                @Override
                public Optional<StockDetail> findDetailByWarehouseCodeAndSku(String warehouseCode, String sku) {
                    if ("WH-SE-001".equals(warehouseCode) && "EO-5W30-1L".equals(sku)) {
                        return Optional.of(new StockDetail("WH-SE-001", "EO-5W30-1L", 48, 50));
                    }
                    return Optional.empty();
                }

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
