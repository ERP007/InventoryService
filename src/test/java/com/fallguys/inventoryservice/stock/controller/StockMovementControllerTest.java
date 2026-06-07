package com.fallguys.inventoryservice.stock.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

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
import com.fallguys.inventoryservice.shared.web.GlobalExceptionHandler;
import com.fallguys.inventoryservice.stock.domain.MovementReason;
import com.fallguys.inventoryservice.stock.domain.MovementType;
import com.fallguys.inventoryservice.stock.domain.StockMovementRepository;
import com.fallguys.inventoryservice.stock.domain.StockMovementService;
import com.fallguys.inventoryservice.stock.domain.query.MovementHistory;
import com.fallguys.inventoryservice.stock.domain.query.MovementSearchQuery;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummary;
import com.fallguys.inventoryservice.stock.domain.query.MovementSummaryPage;

@WebMvcTest(StockMovementController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, StockMovementControllerTest.StubConfig.class})
class StockMovementControllerTest {

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

    @Test
    void 이력조회는_200과_content_단위_합성sourceRef_페이지메타를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/movements").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(88231))
                .andExpect(jsonPath("$.content[0].sku").value("HMC-EN-00214"))
                .andExpect(jsonPath("$.content[0].itemName").value("엔진오일 필터"))
                .andExpect(jsonPath("$.content[0].warehouseCode").value("WH-SE-001"))
                .andExpect(jsonPath("$.content[0].warehouseName").value("서울 1창고"))
                .andExpect(jsonPath("$.content[0].delta").value(-3))
                .andExpect(jsonPath("$.content[0].type").value("ADJUST"))
                .andExpect(jsonPath("$.content[0].unit").value("개"))
                .andExpect(jsonPath("$.content[0].reason").value("DAMAGE"))
                .andExpect(jsonPath("$.content[0].sourceRef").value("ADJ-88231"))
                .andExpect(jsonPath("$.content[0].executorEmpNo").value("HMC2001"))
                .andExpect(jsonPath("$.content[1].type").value("INBOUND"))
                .andExpect(jsonPath("$.content[1].sourceRef").value("SO-202605-00001"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.hasPrevious").value(false))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 이력조회는_가장_낮은_BRANCH_STAFF도_200으로_조회된다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/movements").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isOk());
    }

    @Test
    void 허용되지_않는_type은_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/movements").param("type", "WRONG").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("type"));
    }

    @Test
    void 기간이_역전되면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/movements")
                        .param("from", "2026-06-10").param("to", "2026-06-01")
                        .with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void 허용되지_않는_size는_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/movements").param("size", "33").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("size"));
    }

    @Test
    void 인증토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/stocks/movements"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        StockMovementService stockMovementService() {
            StockMovementRepository repository = new StockMovementRepository() {
                @Override
                public MovementSummaryPage search(MovementSearchQuery query) {
                    MovementSummary adjust = new MovementSummary(
                            88231L, Instant.parse("2026-05-28T14:35:00Z"), "HMC-EN-00214", "엔진오일 필터",
                            "WH-SE-001", "서울 1창고", -3, MovementType.ADJUST, MovementReason.DAMAGE, null, "HMC2001");
                    MovementSummary inbound = new MovementSummary(
                            88230L, Instant.parse("2026-05-20T14:22:00Z"), "HMC-EN-00214", "엔진오일 필터",
                            "WH-SE-001", "서울 1창고", 40, MovementType.INBOUND, null, "SO-202605-00001", "HMC2001");
                    return new MovementSummaryPage(List.of(adjust, inbound), query.page(), query.size(), 2, 1);
                }

                @Override
                public List<MovementHistory> findRecentBySku(String sku, List<String> warehouseCodes, int limit) {
                    return List.of();
                }
            };
            return new StockMovementService(repository);
        }
    }
}
