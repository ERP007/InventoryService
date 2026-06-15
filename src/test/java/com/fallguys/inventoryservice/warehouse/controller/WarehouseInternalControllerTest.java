package com.fallguys.inventoryservice.warehouse.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fallguys.inventoryservice.branchlocation.domain.BranchLocation;
import com.fallguys.inventoryservice.branchlocation.domain.BranchLocationRepository;
import com.fallguys.inventoryservice.shared.web.GlobalExceptionHandler;
import com.fallguys.inventoryservice.shared.security.SecurityConfig;
import com.fallguys.inventoryservice.shared.security.TestJwtDecoderConfig;
import com.fallguys.inventoryservice.warehouse.domain.Warehouse;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseRepository;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseService;
import com.fallguys.inventoryservice.warehouse.domain.command.ChangeWarehouseActiveCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

@WebMvcTest(WarehouseInternalController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TestJwtDecoderConfig.class, WarehouseInternalControllerTest.StubConfig.class})
class WarehouseInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** 내부 호출은 Role 게이팅이 없으므로 어떤 role이든 인증만 되면 된다. */
    private static RequestPostProcessor jwtWithRole(String role) {
        return jwt().jwt(token -> token
                .claim("employee_no", "svc-001")
                .claim("user_role", role));
    }

    @Test
    void 코드로_창고_기본정보를_200으로_반환한다() throws Exception {
        mockMvc.perform(get("/internal/inventory/warehouses/WH-SE-001").with(jwtWithRole("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.code").value("WH-SE-001"))
                .andExpect(jsonPath("$.name").value("서울 1창고"))
                .andExpect(jsonPath("$.type").value("DEALER"))
                .andExpect(jsonPath("$.branchName").value("서울 강남지점"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void Role_게이팅이_없어_BRANCH_STAFF로도_조회된다() throws Exception {
        mockMvc.perform(get("/internal/inventory/warehouses/WH-SE-001").with(jwtWithRole("BRANCH_STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WH-SE-001"));
    }

    @Test
    void 없는_코드는_404와_WAREHOUSE_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/internal/inventory/warehouses/NOPE-999").with(jwtWithRole("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 인증토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/internal/inventory/warehouses/WH-SE-001"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        WarehouseService warehouseService() {
            WarehouseRepository warehouseRepository = new WarehouseRepository() {
                @Override
                public Optional<WarehouseSummaryForEdit> findForEditByCode(String code) {
                    if ("WH-SE-001".equals(code)) {
                        return Optional.of(new WarehouseSummaryForEdit(
                                2L, "WH-SE-001", "서울 1창고", WarehouseType.DEALER, 3L, "서울 강남지점",
                                "서울 강남구 테헤란로 521", true,
                                Instant.parse("2024-03-10T09:00:00Z"), Instant.parse("2025-11-02T14:30:00Z"), 5L));
                    }
                    return Optional.empty();
                }

                // 이 컨트롤러가 사용하지 않는 메서드들 — 최소 구현.
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
                    throw new UnsupportedOperationException();
                }

                @Override
                public WarehouseSummaryForEdit changeActive(String code, ChangeWarehouseActiveCommand command) {
                    throw new UnsupportedOperationException();
                }
            };

            BranchLocationRepository branchLocationRepository = new BranchLocationRepository() {
                @Override
                public boolean existsByName(String name) {
                    return false;
                }

                @Override
                public boolean existsById(Long id) {
                    return false;
                }

                @Override
                public BranchLocation save(BranchLocation branchLocation) {
                    return branchLocation;
                }

                @Override
                public List<BranchLocation> findAll() {
                    return List.of();
                }
            };

            return new WarehouseService(warehouseRepository, branchLocationRepository);
        }
    }
}
