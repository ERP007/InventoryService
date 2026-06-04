package com.fallguys.inventoryservice.branchlocation.controller;

import com.fallguys.inventoryservice.shared.web.GlobalExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fallguys.inventoryservice.branchlocation.domain.BranchLocation;
import com.fallguys.inventoryservice.branchlocation.domain.BranchLocationRepository;
import com.fallguys.inventoryservice.branchlocation.domain.BranchLocationService;

@WebMvcTest(BranchLocationController.class)
@Import({GlobalExceptionHandler.class, BranchLocationControllerTest.StubConfig.class})
class BranchLocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 정상등록은_201과_발급된_id_및_name을_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/branch-locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수원 영통지점\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.name").value("수원 영통지점"));
    }

    @Test
    void 지점명이_공백이면_400과_INVALID_PARAMETER_details를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/branch-locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    void 지점명이_없으면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/branch-locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void 지점명이_중복이면_409와_BRANCH_LOCATION_NAME_DUPLICATE를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/branch-locations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"중복지점\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BRANCH_LOCATION_NAME_DUPLICATE"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void 목록조회는_200과_content_배열을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/branch-locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("서울 강남지점"))
                .andExpect(jsonPath("$.content[1].id").value(2))
                .andExpect(jsonPath("$.content[1].name").value("서울 송파지점"));
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        BranchLocationService branchLocationService() {
            BranchLocationRepository repository = new BranchLocationRepository() {
                @Override
                public boolean existsByName(String name) {
                    return "중복지점".equals(name);
                }

                @Override
                public boolean existsById(Long id) {
                    return false;
                }

                @Override
                public BranchLocation save(BranchLocation branchLocation) {
                    return BranchLocation.of(9L, branchLocation.getName());
                }

                @Override
                public List<BranchLocation> findAll() {
                    return List.of(
                            BranchLocation.of(1L, "서울 강남지점"),
                            BranchLocation.of(2L, "서울 송파지점"));
                }
            };
            return new BranchLocationService(repository);
        }
    }
}
