package com.fallguys.inventoryservice.branchlocation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.branchlocation.domain.BranchLocation;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import(BranchLocationRepositoryAdapter.class)
class BranchLocationRepositoryAdapterTest {

    @Autowired
    private BranchLocationRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager testEntityManager;

    @Test
    void save는_식별자를_발급하고_저장된_도메인을_반환한다() {
        BranchLocation saved = adapter.save(BranchLocation.create("수원 영통지점"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("수원 영통지점");
    }

    @Test
    void existsByName은_저장된_지점명에_true_미존재_지점명에_false를_반환한다() {
        adapter.save(BranchLocation.create("수원 영통지점"));

        assertThat(adapter.existsByName("수원 영통지점")).isTrue();
        assertThat(adapter.existsByName("없는지점")).isFalse();
    }

    @Test
    void findAll은_전체_지점을_id_오름차순으로_반환한다() {
        BranchLocation first = adapter.save(BranchLocation.create("서울 송파지점"));
        BranchLocation second = adapter.save(BranchLocation.create("서울 강남지점"));

        List<BranchLocation> result = adapter.findAll();

        // IDENTITY는 저장 순서대로 id가 증가하므로 id 오름차순 = 저장 순서
        assertThat(result).extracting(BranchLocation::getName)
                .containsExactly("서울 송파지점", "서울 강남지점");
        assertThat(first.getId()).isLessThan(second.getId());
        assertThat(result.get(0).getId()).isLessThan(result.get(1).getId());
    }

    @Test
    void findAll은_지점이_없으면_빈_목록을_반환한다() {
        assertThat(adapter.findAll()).isEmpty();
    }

    @Test
    void findUnassigned는_어느_창고에도_할당되지_않은_지점만_반환한다() {
        BranchLocation assigned = adapter.save(BranchLocation.create("서울 강남지점"));
        adapter.save(BranchLocation.create("서울 금천지점1")); // 미할당
        // assigned 지점에만 창고를 연결한다.
        insertWarehouse("BR-SE-001", "강남창고", "DEALER", assigned.getId());

        List<BranchLocation> result = adapter.findUnassigned();

        assertThat(result).extracting(BranchLocation::getName).containsExactly("서울 금천지점1");
    }

    @Test
    void findUnassigned는_창고가_하나도_없으면_전체_지점을_반환한다() {
        adapter.save(BranchLocation.create("서울 강남지점"));
        adapter.save(BranchLocation.create("서울 금천지점1"));

        assertThat(adapter.findUnassigned()).extracting(BranchLocation::getName)
                .containsExactly("서울 강남지점", "서울 금천지점1");
    }

    private void insertWarehouse(String code, String name, String type, Long branchId) {
        testEntityManager.getEntityManager().createNativeQuery("""
                        INSERT INTO warehouse (code, name, type, branch_id, active, version)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .setParameter(1, code)
                .setParameter(2, name)
                .setParameter(3, type)
                .setParameter(4, branchId)
                .setParameter(5, true)
                .setParameter(6, 0L)
                .executeUpdate();
    }
}
