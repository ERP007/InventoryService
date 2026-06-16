package com.fallguys.inventoryservice.warehouse.controller;

import com.fallguys.inventoryservice.shared.web.GlobalExceptionHandler;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.fallguys.inventoryservice.branchlocation.domain.BranchLocation;
import com.fallguys.inventoryservice.branchlocation.domain.BranchLocationRepository;
import com.fallguys.inventoryservice.shared.exception.OptimisticLockConflictException;
import com.fallguys.inventoryservice.shared.model.UserRole;
import com.fallguys.inventoryservice.shared.security.SecurityConfig;
import com.fallguys.inventoryservice.shared.security.TestJwtDecoderConfig;
import com.fallguys.inventoryservice.warehouse.domain.Warehouse;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseRepository;
import com.fallguys.inventoryservice.warehouse.domain.WarehouseService;
import com.fallguys.inventoryservice.warehouse.domain.command.ChangeWarehouseActiveCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseOption;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

@WebMvcTest(WarehouseController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, TestJwtDecoderConfig.class, WarehouseControllerTest.StubConfig.class})
class WarehouseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** 지정한 Role을 user_role 클레임으로 담은 JWT 인증을 요청에 주입한다. */
    private static RequestPostProcessor roleJwt(UserRole role) {
        return jwt().jwt(token -> token
                .claim("employee_no", "tester")
                .claim("user_role", role.name()));
    }

    // ---- GET (조회) : 전체 Role 허용 ----

    @Test
    void 정상조회는_200과_content_totalElements_sort를_반환한다() throws Exception {
        // 목록은 권한 차등이 없어 가장 낮은 BRANCH_STAFF로도 조회된다.
        mockMvc.perform(get("/inventory/warehouses").param("type", "HQ").with(roleJwt(UserRole.BRANCH_STAFF)))
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
        mockMvc.perform(get("/inventory/warehouses").param("type", "FACTORY").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details[0].field").value("type"))
                .andExpect(jsonPath("$.details[0].value").value("FACTORY"))
                .andExpect(jsonPath("$.details[0].allowed").isArray());
    }

    @Test
    void 허용되지_않는_sort도_400과_details를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/warehouses").param("sort", "address,asc").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("sort"));
    }

    @Test
    void 인증토큰이_없으면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/warehouses"))
                .andExpect(status().isUnauthorized());
    }

    // ---- GET /hq (본사 창고 드롭다운) : 전체 Role 허용 ----

    @Test
    void 본사창고_목록조회는_200과_id_code_name_슬림응답을_반환한다() throws Exception {
        // 전 Role 허용이므로 가장 낮은 BRANCH_STAFF로도 조회된다. (/hq 가 /{code} 보다 우선 매칭됨도 검증)
        mockMvc.perform(get("/inventory/warehouses/hq").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].code").value("WH-HQ-001"))
                .andExpect(jsonPath("$.content[0].name").value("본사 서울 창고"))
                .andExpect(jsonPath("$.content[1].code").value("WH-HQ-002"));
    }

    // ---- GET /options (창고 선택 드롭다운) : 전체 Role 허용 ----

    @Test
    void 창고선택목록조회는_200과_code_소속지점명_슬림응답을_반환한다() throws Exception {
        // 전 Role 허용이므로 가장 낮은 BRANCH_STAFF로도 조회된다. (/options 가 /{code} 보다 우선 매칭됨도 검증)
        mockMvc.perform(get("/inventory/warehouses/options").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("HQ-001"))
                .andExpect(jsonPath("$.content[0].name").value("본사 중앙창고"))   // HQ는 소속 지점이 없어 창고명으로 대체
                .andExpect(jsonPath("$.content[1].code").value("BR-SE-001"))
                .andExpect(jsonPath("$.content[1].name").value("서울 강남지점"));
    }

    // ---- POST (등록) : ADMIN·HQ_MANAGER ----

    @Test
    void 정상등록은_201과_생성된_창고를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/warehouses")
                        .with(roleJwt(UserRole.ADMIN))
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
    void 등록은_HQ_MANAGER도_허용된다() throws Exception {
        mockMvc.perform(post("/inventory/warehouses")
                        .with(roleJwt(UserRole.HQ_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WH-SE-002","name":"서울 2창고","type":"DEALER","branchId":3,"address":"서울 강남구 테헤란로 521"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void 등록은_권한없는_BRANCH_STAFF면_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/warehouses")
                        .with(roleJwt(UserRole.BRANCH_STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WH-SE-002","name":"서울 2창고","type":"DEALER","branchId":3,"address":"서울 강남구 테헤란로 521"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void 필수값_누락은_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(post("/inventory/warehouses")
                        .with(roleJwt(UserRole.ADMIN))
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
                        .with(roleJwt(UserRole.ADMIN))
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
                        .with(roleJwt(UserRole.ADMIN))
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
                        .with(roleJwt(UserRole.ADMIN))
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
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"DUP-001","name":"서울 2창고","type":"DEALER","branchId":3}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_CODE_DUPLICATE"));
    }

    // ---- GET /{code} (단건 조회) : ADMIN·HQ_MANAGER ----

    @Test
    void 단건조회는_200과_전체필드_branchId_address_version을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/warehouses/WH-SE-001").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.code").value("WH-SE-001"))
                .andExpect(jsonPath("$.type").value("DEALER"))
                .andExpect(jsonPath("$.branchId").value(3))
                .andExpect(jsonPath("$.branchName").value("서울 강남지점"))
                .andExpect(jsonPath("$.address").value("서울 강남구 테헤란로 521"))
                .andExpect(jsonPath("$.version").value(5));
    }

    @Test
    void 단건조회는_권한없는_BRANCH_STAFF면_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/warehouses/WH-SE-001").with(roleJwt(UserRole.BRANCH_STAFF)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void 없는_코드는_404와_WAREHOUSE_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(get("/inventory/warehouses/NOPE-999").with(roleJwt(UserRole.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // ---- PUT /{id} (수정) : ADMIN·HQ_MANAGER ----

    @Test
    void 정상수정은_200과_갱신된_창고_version증가를_반환한다() throws Exception {
        mockMvc.perform(put("/inventory/warehouses/WH-SE-001")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"서울 1창고 (강남)","type":"DEALER","branchId":3,"address":"서울 강남구 테헤란로 521","version":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.code").value("WH-SE-001"))
                .andExpect(jsonPath("$.name").value("서울 1창고 (강남)"))
                .andExpect(jsonPath("$.branchId").value(3))
                .andExpect(jsonPath("$.version").value(6));
    }

    @Test
    void 수정은_권한없는_BRANCH_MANAGER면_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(put("/inventory/warehouses/WH-SE-001")
                        .with(roleJwt(UserRole.BRANCH_MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"서울 1창고 (강남)","type":"DEALER","branchId":3,"address":"서울 강남구 테헤란로 521","version":5}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void code를_포함하면_400과_WAREHOUSE_CODE_IMMUTABLE을_반환한다() throws Exception {
        mockMvc.perform(put("/inventory/warehouses/WH-SE-001")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WH-SE-001","name":"서울 1창고","type":"DEALER","branchId":3,"version":5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_CODE_IMMUTABLE"));
    }

    @Test
    void HQ인데_branchId가_있으면_400과_WAREHOUSE_BRANCH_RULE을_반환한다() throws Exception {
        mockMvc.perform(put("/inventory/warehouses/WH-SE-001")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"본사","type":"HQ","branchId":3,"version":5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_BRANCH_RULE"));
    }

    @Test
    void 수정시_존재하지_않는_branchId는_400과_BRANCH_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(put("/inventory/warehouses/WH-SE-001")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"서울 1창고","type":"DEALER","branchId":999,"version":5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BRANCH_NOT_FOUND"));
    }

    @Test
    void version이_없으면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(put("/inventory/warehouses/WH-SE-001")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"서울 1창고","type":"DEALER","branchId":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("version"));
    }

    @Test
    void 없는_창고_수정은_404와_WAREHOUSE_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(put("/inventory/warehouses/NOPE-999")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"서울 1창고","type":"DEALER","branchId":3,"version":5}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void version이_불일치하면_409와_OPTIMISTIC_LOCK_CONFLICT를_반환한다() throws Exception {
        mockMvc.perform(put("/inventory/warehouses/WH-SE-001")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"서울 1창고","type":"DEALER","branchId":3,"version":4}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
    }

    // ---- PATCH /{id}/active (활성 전환) : ADMIN·HQ_MANAGER ----

    @Test
    void 활성전환은_200과_변경된_active_version증가를_반환한다() throws Exception {
        mockMvc.perform(patch("/inventory/warehouses/WH-SE-001/active")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false,"version":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.code").value("WH-SE-001"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(6))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void 활성전환은_권한없는_HQ_STAFF면_403과_FORBIDDEN을_반환한다() throws Exception {
        mockMvc.perform(patch("/inventory/warehouses/WH-SE-001/active")
                        .with(roleJwt(UserRole.HQ_STAFF))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false,"version":5}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void 같은_값으로의_전환은_멱등_no_op_200이며_version이_그대로다() throws Exception {
        mockMvc.perform(patch("/inventory/warehouses/WH-SE-001/active")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":true,"version":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.version").value(5));
    }

    @Test
    void active가_없으면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(patch("/inventory/warehouses/WH-SE-001/active")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.details[0].field").value("active"));
    }

    @Test
    void active_형식이_틀리면_400과_INVALID_PARAMETER를_반환한다() throws Exception {
        mockMvc.perform(patch("/inventory/warehouses/WH-SE-001/active")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":"네","version":5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETER"));
    }

    @Test
    void 없는_창고_전환은_404와_WAREHOUSE_NOT_FOUND를_반환한다() throws Exception {
        mockMvc.perform(patch("/inventory/warehouses/NOPE-999/active")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false,"version":5}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WAREHOUSE_NOT_FOUND"));
    }

    @Test
    void 전환시_version이_불일치하면_409와_OPTIMISTIC_LOCK_CONFLICT를_반환한다() throws Exception {
        mockMvc.perform(patch("/inventory/warehouses/WH-SE-001/active")
                        .with(roleJwt(UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"active":false,"version":4}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("OPTIMISTIC_LOCK_CONFLICT"));
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
                            1L, "HQ-001", "본사 중앙창고", WarehouseType.HQ, null, null, true,
                            Instant.parse("2024-01-15T09:00:00Z"), Instant.parse("2024-01-15T09:00:00Z")));
                }

                @Override
                public List<WarehouseHqSummary> findActiveHq() {
                    return List.of(
                            new WarehouseHqSummary(1L, "WH-HQ-001", "본사 서울 창고"),
                            new WarehouseHqSummary(5L, "WH-HQ-002", "본사 부산 창고"));
                }

                @Override
                public List<WarehouseOption> findActiveOptions() {
                    return List.of(
                            new WarehouseOption("HQ-001", "본사 중앙창고"),    // 본사: 지점이 없어 창고명으로 대체
                            new WarehouseOption("BR-SE-001", "서울 강남지점"));
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
                            id, w.getCode(), w.getName(), w.getType(), branchName, w.getAddress(), w.isActive(),
                            Instant.parse("2026-05-28T14:30:00Z"), Instant.parse("2026-05-28T14:30:00Z")));
                }

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

                @Override
                public WarehouseSummaryForEdit update(String code, UpdateWarehouseCommand command) {
                    if (!"WH-SE-001".equals(code)) {
                        throw new WarehouseNotFoundException(code);
                    }
                    if (!command.version().equals(5L)) {
                        throw new OptimisticLockConflictException("conflict");
                    }
                    String branchName = command.branchId() == null ? null : "서울 강남지점";
                    return new WarehouseSummaryForEdit(
                            2L, "WH-SE-001", command.name(), command.type(), command.branchId(), branchName,
                            command.address(), true,
                            Instant.parse("2024-03-10T09:00:00Z"), Instant.parse("2026-05-28T14:31:00Z"),
                            command.version() + 1);
                }

                @Override
                public WarehouseSummaryForEdit changeActive(String code, ChangeWarehouseActiveCommand command) {
                    if (!command.version().equals(5L)) {
                        throw new OptimisticLockConflictException("conflict");
                    }
                    return new WarehouseSummaryForEdit(
                            2L, "WH-SE-001", "서울 1창고", WarehouseType.DEALER, 3L, "서울 강남지점",
                            "서울 강남구 테헤란로 521", command.active(),
                            Instant.parse("2024-03-10T09:00:00Z"), Instant.parse("2026-05-28T14:32:00Z"),
                            command.version() + 1);
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
