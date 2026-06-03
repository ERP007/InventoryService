package com.fallguys.inventoryservice.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.fallguys.inventoryservice.domain.WarehouseRepository;
import com.fallguys.inventoryservice.domain.WarehouseService;
import com.fallguys.inventoryservice.domain.model.WarehouseType;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

@WebMvcTest(WarehouseController.class)
@Import({GlobalExceptionHandler.class, WarehouseControllerTest.StubConfig.class})
class WarehouseControllerTest {

    @Autowired
    private MockMvc mockMvc;

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

    @TestConfiguration
    static class StubConfig {

        @Bean
        WarehouseService warehouseService() {
            WarehouseRepository repository = query -> List.of(new WarehouseSummary(
                    1L, "HQ-001", "본사 중앙창고", WarehouseType.HQ, null, true,
                    Instant.parse("2024-01-15T09:00:00Z"), Instant.parse("2024-01-15T09:00:00Z")));
            return new WarehouseService(repository);
        }
    }
}
