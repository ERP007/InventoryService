package com.fallguys.inventoryservice.stock.controller;

import com.fallguys.inventoryservice.shared.web.GlobalExceptionHandler;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.fallguys.inventoryservice.shared.exception.OptimisticLockConflictException;
import com.fallguys.inventoryservice.shared.model.UserRole;
import com.fallguys.inventoryservice.shared.security.SecurityConfig;
import com.fallguys.inventoryservice.shared.security.TestJwtDecoderConfig;
import com.fallguys.inventoryservice.stock.domain.ItemInfoProvider;
import com.fallguys.inventoryservice.stock.domain.command.UpdateSafetyStockCommand;
import com.fallguys.inventoryservice.stock.domain.exception.StockNotFoundException;
import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.MovementType;
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockMovementRepository;
import com.fallguys.inventoryservice.stock.domain.StockRepository;
import com.fallguys.inventoryservice.stock.domain.StockService;
import com.fallguys.inventoryservice.stock.domain.StockAdjustmentService;
import com.fallguys.inventoryservice.stock.domain.StockKpiService;
import com.fallguys.inventoryservice.stock.domain.StockMovement;
import com.fallguys.inventoryservice.stock.domain.StockSkuDetailService;
import com.fallguys.inventoryservice.stock.domain.query.SafetyStockEdit;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.StockCreateResult;
import com.fallguys.inventoryservice.stock.domain.query.StockDetail;
import com.fallguys.inventoryservice.stock.domain.query.StockQuantity;
import com.fallguys.inventoryservice.stock.domain.query.StockSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.StockSkuRow;
import com.fallguys.inventoryservice.stock.domain.query.StockStatusCount;
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
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TestJwtDecoderConfig.class, StockControllerTest.StubConfig.class})
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

    // ---- GET (목록 조회) : 전체 Role, Tenancy 차등 ----

    @Test
    void 목록조회는_200과_content_파생status_페이지메타를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1001))
                .andExpect(jsonPath("$.content[0].sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.content[0].itemUnit").value("EA"))
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
    void 재고행없고_Item마스터에도_없으면_404와_STOCK_NOT_FOUND를_반환한다() throws Exception {
        // 재고 행이 없고 통합 활성(StubConfig itemInfoProvider) + 마스터에 없는 sku → 404.
        mockMvc.perform(get("/inventory/stocks/WH-SE-001/UNREGISTERED").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_NOT_FOUND"));
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
                                {"sku":"HMC-EN-00214","itemName":"엔진오일 필터","itemUnit":"EA","warehouseCode":"WH-SE-001","quantity":100,"safetyStock":50}
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
                                {"sku":"HMC-EN-00214","itemName":"엔진오일 필터","itemUnit":"EA","warehouseCode":"WH-SE-001","quantity":100,"safetyStock":50}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void 인증토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","itemName":"엔진오일 필터","itemUnit":"EA","warehouseCode":"WH-SE-001","quantity":100,"safetyStock":50}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 수량이_음수면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","itemName":"엔진오일 필터","itemUnit":"EA","warehouseCode":"WH-SE-001","quantity":-1,"safetyStock":50}
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
                                {"itemName":"엔진오일 필터","itemUnit":"EA","warehouseCode":"WH-SE-001","quantity":100,"safetyStock":50}
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
                                {"sku":"HMC-EN-00214","itemName":"엔진오일 필터","itemUnit":"EA","warehouseCode":"NOPE","quantity":100,"safetyStock":50}
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
                                {"sku":"DUP-SKU","itemName":"엔진오일 필터","itemUnit":"EA","warehouseCode":"WH-SE-001","quantity":100,"safetyStock":50}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_ALREADY_EXISTS"));
    }

    // ---- GET/PATCH /{warehouseCode}/{sku}/safety-stock (안전재고 조정) : ADMIN·HQ_MANAGER 전용 ----

    @Test
    void 안전재고_프리필조회는_200과_현재안전재고_version을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/WH-SE-001/HMC-EN-00214/safety-stock").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.safetyStock").value(50))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void 안전재고_프리필조회_BRANCH는_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/WH-SE-001/HMC-EN-00214/safety-stock").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void 안전재고_프리필조회_재고없으면_404와_STOCK_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/WH-SE-001/NO-SUCH/safety-stock").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_NOT_FOUND"));
    }

    @Test
    void 안전재고_수정은_200과_갱신된_safetyStock_version을_반환한다() throws Exception {
        mockMvc.perform(patch("/inventory/stocks/WH-SE-001/HMC-EN-00214/safety-stock")
                        .with(roleJwt(UserRole.HQ_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"safetyStock":60,"version":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.safetyStock").value(60))
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void 안전재고_수정_version불일치면_409와_OPTIMISTIC_LOCK_CONFLICT를_반환한다() throws Exception {
        mockMvc.perform(patch("/inventory/stocks/WH-SE-001/HMC-EN-00214/safety-stock")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"safetyStock":60,"version":99}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    @Test
    void 안전재고_수정_음수면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(patch("/inventory/stocks/WH-SE-001/HMC-EN-00214/safety-stock")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"safetyStock":-1,"version":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void 안전재고_수정_BRANCH는_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(patch("/inventory/stocks/WH-SE-001/HMC-EN-00214/safety-stock")
                        .with(roleJwt(UserRole.BRANCH_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"safetyStock":60,"version":3}
                                """))
                .andExpect(status().isForbidden());
    }

    // ---- GET /{sku} (상세 패널) : 전체 Role, Tenancy 차등 ----

    @Test
    void 상세패널조회는_200과_창고별재고_파생status_합계_이력을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/HMC-EN-00214").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.itemName").value("엔진오일 필터"))
                .andExpect(jsonPath("$.itemUnit").value("EA"))
                .andExpect(jsonPath("$.majorCategory").doesNotExist())
                .andExpect(jsonPath("$.middleCategory").doesNotExist())
                .andExpect(jsonPath("$.totalQuantity").value(148))
                .andExpect(jsonPath("$.totalSafetyStock").value(150))
                .andExpect(jsonPath("$.warehouse[0].warehouseCode").value("WH-SE-001"))
                .andExpect(jsonPath("$.warehouse[0].quantity").value(48))
                .andExpect(jsonPath("$.warehouse[0].safetyStock").value(50))
                .andExpect(jsonPath("$.warehouse[0].status").value("LOW"))
                .andExpect(jsonPath("$.warehouse[1].warehouseCode").value("HQ-001"))
                .andExpect(jsonPath("$.warehouse[1].status").value("NORMAL"))
                .andExpect(jsonPath("$.history[0].type").value("OUTBOUND"))
                .andExpect(jsonPath("$.history[0].delta").value(-18))
                .andExpect(jsonPath("$.history[0].executorEmpNo").value("AD002"))
                .andExpect(jsonPath("$.history[0].executorName").value("홍길동"));
    }

    @Test
    void 상세패널조회는_BRANCH_STAFF도_200으로_조회된다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/HMC-EN-00214").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isOk());
    }

    @Test
    void 상세패널조회_범위내_재고없으면_404와_STOCK_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/UNKNOWN-SKU").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_NOT_FOUND"));
    }

    @Test
    void 상세패널조회_sku에_하이픈이_없으면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/NOHYPHEN").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("sku"));
    }

    // ---- GET /kpi : 전체 Role, Tenancy 차등 ----

    @Test
    void KPI조회는_200과_4개_메트릭을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/kpi").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSkuCount").value(20))
                .andExpect(jsonPath("$.lowStockCount").value(3))
                .andExpect(jsonPath("$.noStockCount").value(1))
                .andExpect(jsonPath("$.recentAdjustCount").value(8));
    }

    @Test
    void KPI조회는_가장_낮은_BRANCH_STAFF도_200으로_조회된다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/kpi").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isOk());
    }

    // ---- POST /adjustments : ADMIN·HQ_MANAGER 전용 ----

    @Test
    void 조정은_200과_변동전후_상태_이동식별자를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks/adjustments")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","warehouseCode":"WH-SE-002","adjustmentType":"DECREASE","quantity":3,"reason":"DAMAGE","memo":"파손"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementId").value(88231))
                .andExpect(jsonPath("$.stockId").value(1001))
                .andExpect(jsonPath("$.sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.warehouseCode").value("WH-SE-002"))
                .andExpect(jsonPath("$.previousQuantity").value(51))
                .andExpect(jsonPath("$.delta").value(-3))
                .andExpect(jsonPath("$.currentQuantity").value(48))
                .andExpect(jsonPath("$.safetyStock").value(50))
                .andExpect(jsonPath("$.status").value("LOW"))
                .andExpect(jsonPath("$.occurredAt").exists());
    }

    @Test
    void 조정은_HQ_MANAGER도_허용된다() throws Exception {
        mockMvc.perform(post("/inventory/stocks/adjustments")
                        .with(roleJwt(UserRole.HQ_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","warehouseCode":"WH-SE-002","adjustmentType":"INCREASE","quantity":5,"reason":"FOUND"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void 조정은_권한없는_BRANCH_MANAGER면_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks/adjustments")
                        .with(roleJwt(UserRole.BRANCH_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","warehouseCode":"WH-SE-002","adjustmentType":"DECREASE","quantity":3,"reason":"DAMAGE"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void 조정_reason_누락은_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks/adjustments")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","warehouseCode":"WH-SE-002","adjustmentType":"DECREASE","quantity":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void 조정_증가수량_0은_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks/adjustments")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","warehouseCode":"WH-SE-002","adjustmentType":"INCREASE","quantity":0,"reason":"FOUND"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void 조정_재고없으면_404와_STOCK_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks/adjustments")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"NO-SUCH-SKU","warehouseCode":"WH-SE-002","adjustmentType":"DECREASE","quantity":3,"reason":"DAMAGE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_NOT_FOUND"));
    }

    @Test
    void 조정_차감이_가용재고_초과면_409와_INSUFFICIENT_STOCK을_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/stocks/adjustments")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"HMC-EN-00214","warehouseCode":"WH-SE-002","adjustmentType":"DECREASE","quantity":100,"reason":"DAMAGE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        StockService stockService(StockRepository stockRepository, WarehouseRepository warehouseRepository,
                                  ItemInfoProvider itemInfoProvider) {
            return new StockService(stockRepository, warehouseRepository, itemInfoProvider);
        }

        @Bean
        StockSkuDetailService stockSkuDetailService(StockRepository stockRepository,
                                                    StockMovementRepository stockMovementRepository,
                                                    ItemInfoProvider itemInfoProvider) {
            return new StockSkuDetailService(stockRepository, stockMovementRepository, itemInfoProvider);
        }

        @Bean
        ItemInfoProvider itemInfoProvider() {
            return sku -> Optional.empty();
        }

        @Bean
        StockKpiService stockKpiService(StockRepository stockRepository,
                                        StockMovementRepository stockMovementRepository) {
            return new StockKpiService(stockRepository, stockMovementRepository);
        }

        @Bean
        StockAdjustmentService stockAdjustmentService(StockRepository stockRepository,
                                                      StockMovementRepository stockMovementRepository) {
            return new StockAdjustmentService(stockRepository, stockMovementRepository);
        }

        @Bean
        StockRepository stockRepository() {
            return new StockRepository() {
                private Stock saved;

                @Override
                public StockSummaryPage search(StockSearchQuery query) {
                    StockSummary item = new StockSummary(
                            1001L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, "WH-SE-001", "서울 1창고",
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
                public List<StockQuantity> findQuantitiesByWarehouseCodeAndSkus(String warehouseCode, List<String> skus) {
                    return List.of();
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

                @Override
                public List<StockSkuRow> findSkuWarehouseStocks(String sku, List<String> warehouseCodes) {
                    if ("HMC-EN-00214".equals(sku)) {
                        return List.of(
                                new StockSkuRow("엔진오일 필터", ItemUnit.EA, 2L, "WH-SE-001", "서울 1창고", 48, 50),
                                new StockSkuRow("엔진오일 필터", ItemUnit.EA, 1L, "HQ-001", "본사", 100, 100));
                    }
                    return List.of();
                }

                @Override
                public StockStatusCount countByStatus(List<String> warehouseCodes) {
                    return new StockStatusCount(20, 3, 1);
                }

                @Override
                public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
                    if ("HMC-EN-00214".equals(sku) && "WH-SE-002".equals(warehouseCode)) {
                        return Optional.of(Stock.of(1001L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 51, 50));
                    }
                    return Optional.empty();
                }

                @Override
                public Optional<Stock> findBySkuAndWarehouseIdForUpdate(String sku, Long warehouseId) {
                    return Optional.empty();
                }

                @Override
                public Optional<SafetyStockEdit> findSafetyStockEdit(String warehouseCode, String sku) {
                    if ("WH-SE-001".equals(warehouseCode) && "HMC-EN-00214".equals(sku)) {
                        return Optional.of(new SafetyStockEdit(sku, warehouseCode, "엔진오일 필터", ItemUnit.EA, 120, 50, 3L));
                    }
                    return Optional.empty();
                }

                @Override
                public SafetyStockEdit updateSafetyStock(UpdateSafetyStockCommand command) {
                    if (!"HMC-EN-00214".equals(command.sku())) {
                        throw new StockNotFoundException(command.warehouseCode(), command.sku());
                    }
                    if (command.version() != 3L) { // version 불일치 모사
                        throw new OptimisticLockConflictException("conflict");
                    }
                    return new SafetyStockEdit(command.sku(), command.warehouseCode(), "엔진오일 필터", ItemUnit.EA,
                            120, command.safetyStock(), 4L);
                }
            };
        }

        @Bean
        StockMovementRepository stockMovementRepository() {
            return new StockMovementRepository() {
                @Override
                public MovementSummaryPage search(MovementSearchQuery query) {
                    return new MovementSummaryPage(List.of(), query.page(), query.size(), 0, 0);
                }

                @Override
                public List<MovementHistory> findRecentBySku(String sku, List<String> warehouseCodes, int limit) {
                    return List.of(new MovementHistory(
                            MovementType.OUTBOUND, -18, "AD002", "홍길동", Instant.parse("2026-05-20T14:22:00Z")));
                }

                @Override
                public long countRecent(List<String> warehouseCodes, Instant since) {
                    return 8;
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

                @Override
                public StockMovement save(StockMovement movement) {
                    return StockMovement.of(88231L, movement.getSku(), movement.getItemName(), movement.getItemUnit(),
                            movement.getWarehouseId(), movement.getDelta(), movement.getType(), movement.getReason(),
                            movement.getSourceRef(), movement.getSourceLineNo(), movement.getStockAfter(),
                            movement.getNote(), movement.getExecutorEmpNo(), movement.getExecutorName(),
                            Instant.parse("2026-05-28T14:35:00Z"));
                }
            };
        }

        @Bean
        WarehouseRepository warehouseRepository() {
            return new WarehouseRepository() {
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
        }
    }
}
