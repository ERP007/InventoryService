package com.fallguys.inventoryservice.controller;

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

import com.fallguys.inventoryservice.domain.BranchLocation;
import com.fallguys.inventoryservice.domain.BranchLocationRepository;
import com.fallguys.inventoryservice.domain.Warehouse;
import com.fallguys.inventoryservice.domain.WarehouseRepository;
import com.fallguys.inventoryservice.domain.WarehouseService;
import com.fallguys.inventoryservice.domain.model.WarehouseType;
import com.fallguys.inventoryservice.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

@WebMvcTest(WarehouseController.class)
@Import({GlobalExceptionHandler.class, WarehouseControllerTest.StubConfig.class})
class WarehouseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ---- GET (조회) ----

    @Test
    void 정상조회는_200과_content_totalElements_sort를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/warehouses").param("type", "HQ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.sort").value("code,asc"))
                .andExpect(jsonPath("$.content[0].code").value("HQ-001"))
                .andExpect(jsonPath("$.content[0].type").value("HQ"))
                .andExpect(jsonPath("$.content[0].branchName").isEmpty())
                .andExpect(jsonPath("$.content[0].active").value(true));
    }

    @Test
    void 허용되지_않는_type은_400과_INVALID_PARAMETER_details를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/warehouses").param("type", "FACTORY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details[0].field").value("type"))
                .andExpect(jsonPath("$.details[0].value").value("FACTORY"))
                .andExpect(jsonPath("$.details[0].allowed").isArray());
    }

    @Test
    void 허용되지_않는_sort도_400과_details를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/warehouses").param("sort", "address,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("sort"));
    }

    // ---- POST (등록) ----

    @Test
    void 정상등록은_201과_생성된_창고를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WH-SE-002","name":"서울 2창고","type":"DEALER","branchId":3,"address":"서울 강남구 테헤란로 521"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(24))
                .andExpect(jsonPath("$.code").value("WH-SE-002"))
                .andExpect(jsonPath("$.type").value("DEALER"))
                .andExpect(jsonPath("$.branchName").value("서울 강남지점"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void 필수값_누락은_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"서울 2창고","type":"DEALER","branchId":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("code"));
    }

    @Test
    void 허용되지_않는_type은_400과_INVALID_PARAMETER_allowed를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WH-SE-002","name":"서울 2창고","type":"FACTORY","branchId":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("type"))
                .andExpect(jsonPath("$.details[0].allowed").isArray());
    }

    @Test
    void DEALER인데_branchId가_없으면_400과_WAREHOUSE_BRANCH_RULE을_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WH-SE-002","name":"서울 2창고","type":"DEALER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_BRANCH_RULE"));
    }

    @Test
    void 존재하지_않는_branchId는_400과_BRANCH_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WH-SE-002","name":"서울 2창고","type":"DEALER","branchId":999}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BRANCH_NOT_FOUND"));
    }

    @Test
    void 코드가_중복이면_409와_WAREHOUSE_CODE_DUPLICATE를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"DUP-001","name":"서울 2창고","type":"DEALER","branchId":3}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_CODE_DUPLICATE"));
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        WarehouseService warehouseService() {
            WarehouseRepository warehouseRepository = new WarehouseRepository() {
                private final Warehouse[] lastSaved = new Warehouse[1];

                @Override
                public List<WarehouseSummary> search(WarehouseSearchQuery query) {
                    return List.of(new WarehouseSummary(
                            1L, "HQ-001", "본사 중앙창고", WarehouseType.HQ, null, true,
                            Instant.parse("2024-01-15T09:00:00Z"), Instant.parse("2024-01-15T09:00:00Z")));
                }

                @Override
                public boolean existsByCode(String code) {
                    return "DUP-001".equals(code);
                }

                @Override
                public Long save(Warehouse warehouse) {
                    lastSaved[0] = warehouse;
                    return 24L;
                }

                @Override
                public Optional<WarehouseSummary> findSummaryById(Long id) {
                    Warehouse w = lastSaved[0];
                    String branchName = w.getBranchId() == null ? null : "서울 강남지점";
                    return Optional.of(new WarehouseSummary(
                            id, w.getCode(), w.getName(), w.getType(), branchName, w.isActive(),
                            Instant.parse("2026-05-28T14:30:00Z"), Instant.parse("2026-05-28T14:30:00Z")));
                }
            };

            BranchLocationRepository branchLocationRepository = new BranchLocationRepository() {
                @Override
                public boolean existsByName(String name) {
                    return false;
                }

                @Override
                public boolean existsById(Long id) {
                    return id != null && id == 3L;
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
