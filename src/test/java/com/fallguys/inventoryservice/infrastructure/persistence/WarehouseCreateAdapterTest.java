package com.fallguys.inventoryservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.domain.Warehouse;
import com.fallguys.inventoryservice.domain.command.CreateWarehouseCommand;
import com.fallguys.inventoryservice.domain.model.WarehouseType;
import com.fallguys.inventoryservice.domain.query.WarehouseSummary;

/**
 * 창고 등록 영속 검증. 명시적 id를 가진 창고 시드가 없어 IDENTITY 발급이 시드 id와 충돌하지 않도록
 * 조회 테스트(WarehouseRepositoryAdapterTest)와 분리한다. 소속 지점(10)만 시드한다.
 */
@DataJpaTest
@Import(WarehouseRepositoryAdapter.class)
class WarehouseCreateAdapterTest {

    @Autowired
    private WarehouseRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void seedBranch() {
        testEntityManager.getEntityManager()
                .createNativeQuery("INSERT INTO branch_location (id, name) VALUES (?, ?)")
                .setParameter(1, 10L)
                .setParameter(2, "서울 강남지점")
                .executeUpdate();
    }

    @Test
    void DEALER창고_저장시_id와_타임스탬프가_발급되고_재조회하면_지점명이_채워진다() {
        Warehouse warehouse = Warehouse.create(
                new CreateWarehouseCommand("WH-SE-002", "서울 2창고", WarehouseType.DEALER, 10L, "서울 강남구"));

        Long id = adapter.save(warehouse);
        WarehouseSummary summary = adapter.findSummaryById(id).orElseThrow();

        assertThat(id).isNotNull();
        assertThat(summary.code()).isEqualTo("WH-SE-002");
        assertThat(summary.type()).isEqualTo(WarehouseType.DEALER);
        assertThat(summary.branchName()).isEqualTo("서울 강남지점");
        assertThat(summary.active()).isTrue();
        assertThat(summary.createdAt()).isNotNull();
        assertThat(summary.updatedAt()).isNotNull();
    }

    @Test
    void HQ창고는_지점명이_null로_조회된다() {
        Warehouse warehouse = Warehouse.create(
                new CreateWarehouseCommand("HQ-002", "본사 제2창고", WarehouseType.HQ, null, null));

        Long id = adapter.save(warehouse);
        WarehouseSummary summary = adapter.findSummaryById(id).orElseThrow();

        assertThat(summary.branchName()).isNull();
        assertThat(summary.active()).isTrue();
    }

    @Test
    void existsByCode는_저장된_코드에_true_미존재_코드에_false() {
        adapter.save(Warehouse.create(
                new CreateWarehouseCommand("WH-SE-002", "서울 2창고", WarehouseType.DEALER, 10L, null)));

        assertThat(adapter.existsByCode("WH-SE-002")).isTrue();
        assertThat(adapter.existsByCode("WH-NONE")).isFalse();
    }

    @Test
    void findSummaryById는_없는_id면_empty를_반환한다() {
        assertThat(adapter.findSummaryById(999L)).isEmpty();
    }
}
