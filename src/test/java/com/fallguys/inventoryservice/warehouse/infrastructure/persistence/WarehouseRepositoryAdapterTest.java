package com.fallguys.inventoryservice.warehouse.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.shared.exception.OptimisticLockConflictException;
import com.fallguys.inventoryservice.warehouse.domain.command.ChangeWarehouseActiveCommand;
import com.fallguys.inventoryservice.warehouse.domain.command.UpdateWarehouseCommand;
import com.fallguys.inventoryservice.warehouse.domain.exception.WarehouseNotFoundException;
import com.fallguys.inventoryservice.warehouse.domain.model.WarehouseType;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseHqSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseOption;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummary;
import com.fallguys.inventoryservice.warehouse.domain.query.WarehouseSummaryForEdit;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import(WarehouseRepositoryAdapter.class)
class WarehouseRepositoryAdapterTest {

    @Autowired
    private WarehouseRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void seed() {
        insertBranch(10L, "서울 강남지점");
        insertWarehouse(1L, "HQ-001", "본사 중앙창고", "HQ", null, true, "2024-01-15T09:00:00Z");
        insertWarehouse(2L, "HW-SE-001", "서울 1창고", "DEALER", 10L, true, "2024-03-10T09:00:00Z");
        insertWarehouse(3L, "HW-SE-002", "서울 2창고", "DEALER", 10L, false, "2024-05-01T09:00:00Z");
    }

    @Test
    void 기본조회는_code오름차순_전체상태이며_HQ는_지점명이_null이고_DEALER는_지점명이_채워진다() {
        List<WarehouseSummary> result = adapter.search(WarehouseSearchQuery.of(null, null, null, null));

        assertThat(result).extracting(WarehouseSummary::code)
                .containsExactly("HQ-001", "HW-SE-001", "HW-SE-002");
        assertThat(result.get(0).type()).isEqualTo(WarehouseType.HQ);
        assertThat(result.get(0).branchName()).isNull();
        assertThat(result.get(1).branchName()).isEqualTo("서울 강남지점");
    }

    @Test
    void status_ACTIVE는_활성_창고만_반환한다() {
        List<WarehouseSummary> result = adapter.search(WarehouseSearchQuery.of(null, null, "ACTIVE", null));

        assertThat(result).extracting(WarehouseSummary::code).containsExactly("HQ-001", "HW-SE-001");
    }

    @Test
    void status_INACTIVE는_비활성_창고만_반환한다() {
        List<WarehouseSummary> result = adapter.search(WarehouseSearchQuery.of(null, null, "INACTIVE", null));

        assertThat(result).extracting(WarehouseSummary::code).containsExactly("HW-SE-002");
    }

    @Test
    void type필터로_HQ또는_DEALER만_조회한다() {
        assertThat(adapter.search(WarehouseSearchQuery.of(null, "HQ", null, null)))
                .extracting(WarehouseSummary::code).containsExactly("HQ-001");
        assertThat(adapter.search(WarehouseSearchQuery.of(null, "DEALER", null, null)))
                .extracting(WarehouseSummary::code).containsExactly("HW-SE-001", "HW-SE-002");
    }

    @Test
    void keyword는_창고명_부분일치로_조회한다() {
        List<WarehouseSummary> result = adapter.search(WarehouseSearchQuery.of("본사", null, null, null));

        assertThat(result).extracting(WarehouseSummary::code).containsExactly("HQ-001");
    }

    @Test
    void keyword는_창고코드_부분일치이며_대소문자를_무시한다() {
        List<WarehouseSummary> result = adapter.search(WarehouseSearchQuery.of("hw-se", null, null, null));

        assertThat(result).extracting(WarehouseSummary::code).containsExactly("HW-SE-001", "HW-SE-002");
    }

    @Test
    void sort는_name기준_내림차순으로_적용된다() {
        List<WarehouseSummary> result = adapter.search(WarehouseSearchQuery.of(null, null, null, "name,desc"));

        assertThat(result).extracting(WarehouseSummary::name)
                .containsExactly("서울 2창고", "서울 1창고", "본사 중앙창고");
    }

    @Test
    void 매칭이_없으면_빈_리스트를_반환한다() {
        List<WarehouseSummary> result = adapter.search(WarehouseSearchQuery.of("존재하지않는창고", null, null, null));

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveHq는_활성_본사창고만_code오름차순으로_반환한다() {
        // 비활성 HQ를 추가해 active 필터를 검증한다(seed에는 활성 HQ-001 + DEALER 2건만 있음).
        insertWarehouse(4L, "HQ-002", "본사 비활성창고", "HQ", null, false, "2024-06-01T09:00:00Z");

        List<WarehouseHqSummary> result = adapter.findActiveHq();

        // 활성 HQ만(HQ-001), 비활성 HQ(HQ-002)·DEALER(HW-SE-*)는 제외
        assertThat(result).extracting(WarehouseHqSummary::code).containsExactly("HQ-001");
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).name()).isEqualTo("본사 중앙창고");
    }

    @Test
    void findActiveOptions는_활성_창고만_code오름차순으로_반환하고_HQ는_창고명으로_대체한다() {
        List<WarehouseOption> result = adapter.findActiveOptions();

        // 활성만(HW-SE-002 비활성 제외), code 오름차순
        assertThat(result).extracting(WarehouseOption::code).containsExactly("HQ-001", "HW-SE-001");
        assertThat(result.get(0).name()).isEqualTo("본사 중앙창고");  // HQ는 소속 지점이 없어 창고명으로 대체
        assertThat(result.get(1).name()).isEqualTo("서울 강남지점");  // DEALER는 소속 지점명
    }

    @Test
    void findForEditByCode는_branchId_address_version까지_조인하여_반환한다() {
        WarehouseSummaryForEdit detail = adapter.findForEditByCode("HW-SE-001").orElseThrow();

        assertThat(detail.id()).isEqualTo(2L);
        assertThat(detail.code()).isEqualTo("HW-SE-001");
        assertThat(detail.type()).isEqualTo(WarehouseType.DEALER);
        assertThat(detail.branchId()).isEqualTo(10L);
        assertThat(detail.branchName()).isEqualTo("서울 강남지점");
        assertThat(detail.address()).isEqualTo("서울 어딘가");
        assertThat(detail.active()).isTrue();
        assertThat(detail.version()).isEqualTo(0L);
    }

    @Test
    void findForEditByCode는_없는_code면_empty를_반환한다() {
        assertThat(adapter.findForEditByCode("NOPE-999")).isEmpty();
    }

    @Test
    void update는_변경항목을_수정하고_version과_updatedAt을_올린다() {
        WarehouseSummaryForEdit result = adapter.update("HW-SE-001",
                new UpdateWarehouseCommand("서울 1창고 (강남)", WarehouseType.DEALER, 10L, "새 주소", 0L));

        assertThat(result.name()).isEqualTo("서울 1창고 (강남)");
        assertThat(result.address()).isEqualTo("새 주소");
        assertThat(result.branchName()).isEqualTo("서울 강남지점");
        assertThat(result.version()).isEqualTo(1L);
        assertThat(result.updatedAt()).isAfter(result.createdAt());
    }

    @Test
    void update는_version이_불일치하면_OptimisticLockConflictException을_던진다() {
        assertThatThrownBy(() -> adapter.update("HW-SE-001",
                new UpdateWarehouseCommand("서울 1창고", WarehouseType.DEALER, 10L, null, 99L)))
                .isInstanceOf(OptimisticLockConflictException.class);
    }

    @Test
    void update는_없는_창고면_WarehouseNotFoundException을_던진다() {
        assertThatThrownBy(() -> adapter.update("NOPE-999",
                new UpdateWarehouseCommand("서울 1창고", WarehouseType.DEALER, 10L, null, 0L)))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void changeActive는_상태를_전환하고_version과_updatedAt을_올린다() {
        WarehouseSummaryForEdit result = adapter.changeActive("HW-SE-001",
                new ChangeWarehouseActiveCommand(false, 0L));

        assertThat(result.active()).isFalse();
        assertThat(result.version()).isEqualTo(1L);
        assertThat(result.updatedAt()).isAfter(result.createdAt());
    }

    @Test
    void changeActive는_version이_불일치하면_OptimisticLockConflictException을_던진다() {
        assertThatThrownBy(() -> adapter.changeActive("HW-SE-001",
                new ChangeWarehouseActiveCommand(false, 99L)))
                .isInstanceOf(OptimisticLockConflictException.class);
    }

    @Test
    void changeActive는_없는_창고면_WarehouseNotFoundException을_던진다() {
        assertThatThrownBy(() -> adapter.changeActive("NOPE-999",
                new ChangeWarehouseActiveCommand(false, 0L)))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void search는_창고_주소를_함께_반환한다() {
        List<WarehouseSummary> result = adapter.search(WarehouseSearchQuery.of("hw-se-001", null, null, null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).address()).isEqualTo("서울 어딘가");
    }

    @Test
    void sort_branch는_소속지점명_오름차순으로_적용된다() {
        insertBranch(20L, "부산 해운대지점");
        insertWarehouse(5L, "BR-BU-001", "부산 해운대창고", "DEALER", 20L, true, "2024-07-01T09:00:00Z");

        List<WarehouseSummary> result = adapter.search(WarehouseSearchQuery.of(null, "DEALER", null, "branch,asc"));

        // 소속 지점명 오름차순(부산 < 서울). 서울 강남지점 2건(HW-SE-001/002)은 동률.
        assertThat(result).extracting(WarehouseSummary::branchName)
                .containsExactly("부산 해운대지점", "서울 강남지점", "서울 강남지점");
    }

    @Test
    void existsByBranchIdExcludingCode_등록검사는_지점에_창고가_있으면_true_없으면_false다() {
        // 서울 강남지점(10L)엔 창고가 있고, 999L엔 없다.
        assertThat(adapter.existsByBranchIdExcludingCode(10L, null)).isTrue();
        assertThat(adapter.existsByBranchIdExcludingCode(999L, null)).isFalse();
    }

    @Test
    void existsByBranchIdExcludingCode_수정검사는_자기_창고를_제외하고_판정한다() {
        insertBranch(20L, "부산 해운대지점");
        insertWarehouse(5L, "BR-BU-001", "부산 해운대창고", "DEALER", 20L, true, "2024-07-01T09:00:00Z");

        // 20L엔 BR-BU-001만 할당 → 자기 제외 시 false, 다른 코드 기준이면 true.
        assertThat(adapter.existsByBranchIdExcludingCode(20L, "BR-BU-001")).isFalse();
        assertThat(adapter.existsByBranchIdExcludingCode(20L, "OTHER")).isTrue();
    }

    private void insertBranch(long id, String name) {
        entityManager().createNativeQuery("INSERT INTO branch_location (id, name) VALUES (?, ?)")
                .setParameter(1, id)
                .setParameter(2, name)
                .executeUpdate();
    }

    private void insertWarehouse(long id, String code, String name, String type,
                                 Long branchId, boolean active, String createdAt) {
        Instant timestamp = Instant.parse(createdAt);
        entityManager().createNativeQuery("""
                        INSERT INTO warehouse
                            (id, code, name, type, branch_id, address, active,
                             created_by, updated_by, created_at, updated_at, version)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, id)
                .setParameter(2, code)
                .setParameter(3, name)
                .setParameter(4, type)
                .setParameter(5, branchId)
                .setParameter(6, "서울 어딘가")
                .setParameter(7, active)
                .setParameter(8, "EMP-001")
                .setParameter(9, "EMP-001")
                .setParameter(10, timestamp)
                .setParameter(11, timestamp)
                .setParameter(12, 0L)
                .executeUpdate();
    }

    private EntityManager entityManager() {
        return testEntityManager.getEntityManager();
    }
}
