package com.fallguys.inventoryservice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.fallguys.inventoryservice.domain.BranchLocation;

@DataJpaTest
@Import(BranchLocationRepositoryAdapter.class)
class BranchLocationRepositoryAdapterTest {

    @Autowired
    private BranchLocationRepositoryAdapter adapter;

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
}
