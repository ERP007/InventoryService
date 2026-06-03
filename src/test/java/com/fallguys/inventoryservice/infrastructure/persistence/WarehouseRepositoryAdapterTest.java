package com.fallguys.inventoryservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.domain.model.WarehouseType;
import com.fallguys.inventoryservice.domain.query.WarehouseSearchQuery;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

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
