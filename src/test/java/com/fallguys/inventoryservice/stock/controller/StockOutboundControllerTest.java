package com.fallguys.inventoryservice.stock.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fallguys.inventoryservice.shared.security.SecurityConfig;
import com.fallguys.inventoryservice.shared.web.GlobalExceptionHandler;
import com.fallguys.inventoryservice.stock.domain.ItemUnit;
import com.fallguys.inventoryservice.stock.domain.Stock;
import com.fallguys.inventoryservice.stock.domain.StockMovement;
import com.fallguys.inventoryservice.stock.domain.StockMovementRepository;
import com.fallguys.inventoryservice.stock.domain.StockOutboundService;
import com.fallguys.inventoryservice.stock.domain.StockRepository;
import com.fallguys.inventoryservice.stock.domain.query.InboundMovement;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;
import com.fallguys.inventoryservice.stock.domain.query.OutboundMovement;
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
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

@WebMvcTest(StockOutboundController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, StockOutboundControllerTest.StubConfig.class})
class StockOutboundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** 내부 호출 — Role 무관, 단 executor 스냅샷용 employee_no·name 클레임이 필요하다. */
    private static RequestPostProcessor svcJwt() {
        return jwt().jwt(token -> token
                .claim("employee_no", "HMC1001")
                .claim("name", "김본사")
                .claim("user_role", "ADMIN"));
    }

    @Test
    void 정상출고는_200과_음수delta_movements를_반환한다() throws Exception {
        mockMvc.perform(post("/internal/inventory/stocks/outbound")
                        .with(svcJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceRef":"SO-2026-0034","warehouseCode":"WH-SE-002",
                                 "lines":[{"sku":"HMC-EN-00214","quantity":20,"sourceLineNo":1}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceRef").value("SO-2026-0034"))
                .andExpect(jsonPath("$.warehouseCode").value("WH-SE-002"))
                .andExpect(jsonPath("$.movements[0].sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.movements[0].delta").value(-20))
                .andExpect(jsonPath("$.movements[0].currentQuantity").value(80));
    }

    @Test
    void 멱등_같은sourceRef는_이전결과를_replay한다() throws Exception {
        mockMvc.perform(post("/internal/inventory/stocks/outbound")
                        .with(svcJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceRef":"SO-REPLAY","warehouseCode":"WH-SE-002",
                                 "lines":[{"sku":"HMC-EN-00214","quantity":20,"sourceLineNo":1}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movements[0].movementId").value(88250))
                .andExpect(jsonPath("$.movements[0].delta").value(-20));
    }

    @Test
    void lines가_비어있으면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(post("/internal/inventory/stocks/outbound")
                        .with(svcJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceRef":"SO-1","warehouseCode":"WH-SE-002","lines":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void 수량이_0이하면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(post("/internal/inventory/stocks/outbound")
                        .with(svcJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceRef":"SO-1","warehouseCode":"WH-SE-002",
                                 "lines":[{"sku":"HMC-EN-00214","quantity":0,"sourceLineNo":1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void sourceRef가_없으면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(post("/internal/inventory/stocks/outbound")
                        .with(svcJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"warehouseCode":"WH-SE-002",
                                 "lines":[{"sku":"HMC-EN-00214","quantity":20,"sourceLineNo":1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void 없는_창고는_404와_WAREHOUSE_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(post("/internal/inventory/stocks/outbound")
                        .with(svcJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceRef":"SO-1","warehouseCode":"NOPE-999",
                                 "lines":[{"sku":"HMC-EN-00214","quantity":20,"sourceLineNo":1}]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void 비활성_창고는_400과_WAREHOUSE_INACTIVE를_반환한다() throws Exception {
        mockMvc.perform(post("/internal/inventory/stocks/outbound")
                        .with(svcJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceRef":"SO-1","warehouseCode":"WH-INACTIVE",
                                 "lines":[{"sku":"HMC-EN-00214","quantity":20,"sourceLineNo":1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_INACTIVE"));
    }

    @Test
    void 재고행이_없으면_404와_STOCK_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(post("/internal/inventory/stocks/outbound")
                        .with(svcJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceRef":"SO-1","warehouseCode":"WH-SE-002",
                                 "lines":[{"sku":"NO-SUCH","quantity":20,"sourceLineNo":1}]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STOCK_NOT_FOUND"));
    }

    @Test
    void 가용재고_부족이면_409와_INSUFFICIENT_STOCK을_반환한다() throws Exception {
        // LOW-STOCK은 현재고 5인데 20을 출고 → 가용재고 초과.
        mockMvc.perform(post("/internal/inventory/stocks/outbound")
                        .with(svcJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceRef":"SO-1","warehouseCode":"WH-SE-002",
                                 "lines":[{"sku":"LOW-STOCK","quantity":20,"sourceLineNo":1}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void 비관락_타임아웃이면_409와_LOCK_TIMEOUT을_반환한다() throws Exception {
        // LOCK-TIMEOUT sku는 stub이 CannotAcquireLockException을 던진다(잠금 대기 초과 모사).
        mockMvc.perform(post("/internal/inventory/stocks/outbound")
                        .with(svcJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceRef":"SO-1","warehouseCode":"WH-SE-002",
                                 "lines":[{"sku":"LOCK-TIMEOUT","quantity":1,"sourceLineNo":1}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("LOCK_TIMEOUT"));
    }

    @Test
    void 인증토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(post("/internal/inventory/stocks/outbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceRef":"SO-1","warehouseCode":"WH-SE-002",
                                 "lines":[{"sku":"HMC-EN-00214","quantity":20,"sourceLineNo":1}]}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        StockOutboundService stockOutboundService() {
            StubStockRepository stockRepository = new StubStockRepository();
            stockRepository.put(Stock.of(11L, "HMC-EN-00214", "엔진오일 필터", ItemUnit.EA, 2L, 100, 50));
            stockRepository.put(Stock.of(12L, "LOW-STOCK", "부족 부품", ItemUnit.EA, 2L, 5, 10));
            return new StockOutboundService(
                    stockRepository, new StubMovementRepository(), new StubWarehouseRepository());
        }
    }

    private static final class StubStockRepository implements StockRepository {
        private final Map<String, Stock> stocks = new HashMap<>();

        void put(Stock stock) {
            stocks.put(stock.getSku(), stock);
        }

        @Override
        public Optional<Stock> findBySkuAndWarehouseIdForUpdate(String sku, Long warehouseId) {
            if ("LOCK-TIMEOUT".equals(sku)) {
                throw new CannotAcquireLockException("lock wait timeout (모사)");
            }
            return Optional.ofNullable(stocks.get(sku));
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

        @Override
        public Optional<Stock> findBySkuAndWarehouseCode(String sku, String warehouseCode) {
            return Optional.ofNullable(stocks.get(sku));
        }

        @Override
        public Long save(Stock stock) {
            return stock.getId();
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
    }

    private static final class StubMovementRepository implements StockMovementRepository {
        private long nextId = 88250L;

        @Override
        public List<OutboundMovement> findOutboundBySourceRefAndWarehouseCode(String sourceRef, String warehouseCode) {
            if ("SO-REPLAY".equals(sourceRef)) {
                return List.of(new OutboundMovement(88250L, "HMC-EN-00214", -20, 80));
            }
            return List.of();
        }

        @Override
        public StockMovement save(StockMovement movement) {
            return StockMovement.of(
                    nextId++, movement.getSku(), movement.getItemName(), movement.getItemUnit(),
                    movement.getWarehouseId(), movement.getDelta(), movement.getType(), movement.getReason(),
                    movement.getSourceRef(), movement.getSourceLineNo(), movement.getStockAfter(),
                    movement.getNote(), movement.getExecutorEmpNo(), movement.getExecutorName(),
                    Instant.parse("2026-06-10T00:00:00Z"));
        }

        @Override
        public List<InboundMovement> findInboundBySourceRefAndWarehouseCode(String sourceRef, String warehouseCode) {
            return List.of();
        }

        @Override
        public MovementSummaryPage search(MovementSearchQuery query) {
            return null;
        }

        @Override
        public List<MovementHistory> findRecentBySku(String sku, List<String> warehouseCodes, int limit) {
            return List.of();
        }

        @Override
        public long countRecent(List<String> warehouseCodes, Instant since) {
            return 0;
        }
    }

    private static final class StubWarehouseRepository implements WarehouseRepository {

        @Override
        public Optional<WarehouseSummaryForEdit> findForEditByCode(String code) {
            if ("WH-SE-002".equals(code)) {
                return warehouse(2L, code, true);
            }
            if ("WH-INACTIVE".equals(code)) {
                return warehouse(9L, code, false);
            }
            return Optional.empty();
        }

        private Optional<WarehouseSummaryForEdit> warehouse(Long id, String code, boolean active) {
            return Optional.of(new WarehouseSummaryForEdit(
                    id, code, "서울 2창고", WarehouseType.DEALER, 3L, "서울 강남지점", "주소", active,
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
            return "WH-SE-002".equals(code) || "WH-INACTIVE".equals(code);
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
    }
}
